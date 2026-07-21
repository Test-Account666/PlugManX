package velocity.com.rylinaux.plugman.pluginmanager;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class VelocityPacketRegistryCleaner {
    private static final String STATE_REGISTRY_CLASS =
            "com.velocitypowered.proxy.protocol.StateRegistry";
    private static final String PACKET_REGISTRY_CLASS =
            "com.velocitypowered.proxy.protocol.StateRegistry$PacketRegistry";
    private static final String PROTOCOL_REGISTRY_CLASS =
            "com.velocitypowered.proxy.protocol.StateRegistry$PacketRegistry$ProtocolRegistry";

    private final Object[] stateRegistries;
    private final Field clientbound;
    private final Field serverbound;
    private final Field versions;
    private final Field packetIdToSupplier;
    private final Field packetClassToId;
    private final Method packetSupplierGet;
    private final Method packetSupplierPut;
    private final Method packetSupplierRemove;

    VelocityPacketRegistryCleaner() throws ReflectiveOperationException {
        var stateRegistryClass = Class.forName(STATE_REGISTRY_CLASS);
        var packetRegistryClass = Class.forName(PACKET_REGISTRY_CLASS);
        var protocolRegistryClass = Class.forName(PROTOCOL_REGISTRY_CLASS);

        stateRegistries = stateRegistryClass.getEnumConstants();
        if (stateRegistries == null) {
            throw new ReflectiveOperationException(STATE_REGISTRY_CLASS + " is not an enum");
        }

        clientbound = accessible(stateRegistryClass.getDeclaredField("clientbound"));
        serverbound = accessible(stateRegistryClass.getDeclaredField("serverbound"));
        versions = accessible(packetRegistryClass.getDeclaredField("versions"));
        packetIdToSupplier = accessible(protocolRegistryClass.getDeclaredField("packetIdToSupplier"));
        packetClassToId = accessible(protocolRegistryClass.getDeclaredField("packetClassToId"));

        var supplierRegistryType = packetIdToSupplier.getType();
        packetSupplierGet = supplierRegistryType.getMethod("get", int.class);
        packetSupplierPut = supplierRegistryType.getMethod("put", int.class, Object.class);
        packetSupplierRemove = supplierRegistryType.getMethod("remove", int.class);
    }

    RegistrySnapshot snapshot() throws ReflectiveOperationException {
        var mappings = new LinkedHashMap<MappingKey, RegistryMapping>();
        for (var protocolRegistry : protocolRegistries()) {
            captureMappings(protocolRegistry, mappings);
        }
        return new RegistrySnapshot(Map.copyOf(mappings));
    }

    RegistryDelta addedMappings(RegistrySnapshot before, ClassLoader owner)
            throws ReflectiveOperationException {
        var after = snapshot();
        var additions = after.mappings().entrySet().stream()
                .filter(entry -> !before.mappings().containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(mapping -> mapping.isOwnedBy(owner))
                .toList();
        return new RegistryDelta(additions);
    }

    CleanupResult removeTrackedMappings(RegistryDelta delta) throws ReflectiveOperationException {
        var removedMappings = new ArrayList<RemovedMapping>();
        var skippedMappings = 0;
        try {
            for (var mapping : delta.mappings()) {
                if (!mapping.isStillRegistered(this)) {
                    skippedMappings++;
                    continue;
                }
                removeMapping(mapping.classToId(), mapping.suppliers(), mapping.packetClass(),
                        mapping.packetId(), mapping.supplier());
                removedMappings.add(mapping.asRemoved());
            }
            return new CleanupResult(removedMappings.size(), skippedMappings);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            rollback(removedMappings, exception);
            throw exception;
        }
    }

    CleanupResult removeOwnedMappings(ClassLoader owner) throws ReflectiveOperationException {
        var removedMappings = new ArrayList<RemovedMapping>();
        var skippedMappings = 0;
        try {
            for (var protocolRegistry : protocolRegistries()) {
                skippedMappings += removeOwnedMappings(protocolRegistry, owner, removedMappings);
            }
            return new CleanupResult(removedMappings.size(), skippedMappings);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            rollback(removedMappings, exception);
            throw exception;
        }
    }

    @SuppressWarnings("unchecked")
    private void captureMappings(Object protocolRegistry,
                                 Map<MappingKey, RegistryMapping> mappings)
            throws ReflectiveOperationException {
        var classToId = (Map<Class<?>, Integer>) packetClassToId.get(protocolRegistry);
        var suppliers = packetIdToSupplier.get(protocolRegistry);
        for (var mapping : List.copyOf(classToId.entrySet())) {
            var packetClass = mapping.getKey();
            var packetId = mapping.getValue();
            var supplier = invoke(packetSupplierGet, suppliers, packetId);
            var key = new MappingKey(protocolRegistry, packetClass);
            mappings.put(key, new RegistryMapping(
                    classToId, suppliers, packetClass, packetId, supplier));
        }
    }

    @SuppressWarnings("unchecked")
    private int removeOwnedMappings(Object protocolRegistry,
                                    ClassLoader owner,
                                    List<RemovedMapping> removedMappings)
            throws ReflectiveOperationException {
        var classToId = (Map<Class<?>, Integer>) packetClassToId.get(protocolRegistry);
        var skippedMappings = 0;
        for (var mapping : List.copyOf(classToId.entrySet())) {
            var packetClass = mapping.getKey();
            if (packetClass.getClassLoader() != owner) continue;

            var packetId = mapping.getValue();
            var suppliers = packetIdToSupplier.get(protocolRegistry);
            var supplier = invoke(packetSupplierGet, suppliers, packetId);
            if (supplier == null || supplier.getClass().getClassLoader() != owner) {
                skippedMappings++;
                continue;
            }

            removeMapping(classToId, suppliers, packetClass, packetId, supplier);
            removedMappings.add(new RemovedMapping(classToId, suppliers, packetClass, packetId, supplier));
        }
        return skippedMappings;
    }

    private List<Object> protocolRegistries() throws ReflectiveOperationException {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        var protocolRegistries = new ArrayList<>();
        for (var stateRegistry : stateRegistries) {
            collectProtocolRegistries(clientbound.get(stateRegistry), visited, protocolRegistries);
            collectProtocolRegistries(serverbound.get(stateRegistry), visited, protocolRegistries);
        }
        return protocolRegistries;
    }

    @SuppressWarnings("unchecked")
    private void collectProtocolRegistries(Object packetRegistry,
                                           Set<Object> visited,
                                           List<Object> protocolRegistries) throws IllegalAccessException {
        for (var protocolRegistry : ((Map<Object, Object>) versions.get(packetRegistry)).values()) {
            if (visited.add(protocolRegistry)) protocolRegistries.add(protocolRegistry);
        }
    }

    private void removeMapping(Map<Class<?>, Integer> classToId,
                               Object suppliers,
                               Class<?> packetClass,
                               int packetId,
                               Object expectedSupplier) throws ReflectiveOperationException {
        var removedId = classToId.remove(packetClass);
        Object removedSupplier;
        try {
            removedSupplier = invoke(packetSupplierRemove, suppliers, packetId);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (removedId != null) classToId.put(packetClass, removedId);
            throw exception;
        }

        if (removedId == null || removedId != packetId || removedSupplier != expectedSupplier) {
            if (removedId != null) classToId.put(packetClass, removedId);
            if (removedSupplier != null) invoke(packetSupplierPut, suppliers, packetId, removedSupplier);
            throw new ReflectiveOperationException("Velocity packet registry changed during cleanup");
        }
    }

    private void rollback(List<RemovedMapping> removedMappings, Throwable original) {
        for (var index = removedMappings.size() - 1; index >= 0; index--) {
            var mapping = removedMappings.get(index);
            try {
                mapping.classToId().put(mapping.packetClass(), mapping.packetId());
                invoke(packetSupplierPut, mapping.suppliers(), mapping.packetId(), mapping.supplier());
            } catch (ReflectiveOperationException | RuntimeException rollbackFailure) {
                original.addSuppressed(rollbackFailure);
            }
        }
    }

    private static Object invoke(Method method, Object target, Object... arguments)
            throws ReflectiveOperationException {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            var cause = exception.getCause();
            if (cause instanceof ReflectiveOperationException reflectiveOperationException) {
                throw reflectiveOperationException;
            }
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw exception;
        }
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T object) {
        object.setAccessible(true);
        return object;
    }

    record CleanupResult(int removedMappings, int skippedMappings) {
    }

    record RegistrySnapshot(Map<MappingKey, RegistryMapping> mappings) {
    }

    record RegistryDelta(List<RegistryMapping> mappings) {
        RegistryDelta {
            mappings = List.copyOf(mappings);
        }

        int size() {
            return mappings.size();
        }

        boolean isEmpty() {
            return mappings.isEmpty();
        }
    }

    private static final class MappingKey {
        private final Object protocolRegistry;
        private final Class<?> packetClass;

        private MappingKey(Object protocolRegistry, Class<?> packetClass) {
            this.protocolRegistry = protocolRegistry;
            this.packetClass = packetClass;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof MappingKey other
                    && protocolRegistry == other.protocolRegistry
                    && packetClass == other.packetClass;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(protocolRegistry) + System.identityHashCode(packetClass);
        }
    }

    private record RegistryMapping(
            Map<Class<?>, Integer> classToId,
            Object suppliers,
            Class<?> packetClass,
            int packetId,
            Object supplier
    ) {
        private boolean isOwnedBy(ClassLoader owner) {
            return packetClass.getClassLoader() == owner
                    && supplier != null
                    && supplier.getClass().getClassLoader() == owner;
        }

        private boolean isStillRegistered(VelocityPacketRegistryCleaner cleaner)
                throws ReflectiveOperationException {
            var currentId = classToId.get(packetClass);
            return currentId != null
                    && currentId == packetId
                    && invoke(cleaner.packetSupplierGet, suppliers, packetId) == supplier;
        }

        private RemovedMapping asRemoved() {
            return new RemovedMapping(classToId, suppliers, packetClass, packetId, supplier);
        }
    }

    private record RemovedMapping(
            Map<Class<?>, Integer> classToId,
            Object suppliers,
            Class<?> packetClass,
            int packetId,
            Object supplier
    ) {
    }
}
