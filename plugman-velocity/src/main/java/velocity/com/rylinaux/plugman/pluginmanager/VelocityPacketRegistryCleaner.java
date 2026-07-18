package velocity.com.rylinaux.plugman.pluginmanager;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    CleanupResult removeOwnedMappings(ClassLoader owner) throws ReflectiveOperationException {
        var removedMappings = new ArrayList<RemovedMapping>();
        var skippedMappings = 0;
        try {
            for (var stateRegistry : stateRegistries) {
                skippedMappings += removeOwnedMappings(clientbound.get(stateRegistry), owner, removedMappings);
                skippedMappings += removeOwnedMappings(serverbound.get(stateRegistry), owner, removedMappings);
            }
            return new CleanupResult(removedMappings.size(), skippedMappings);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            rollback(removedMappings, exception);
            throw exception;
        }
    }

    @SuppressWarnings("unchecked")
    private int removeOwnedMappings(Object packetRegistry,
                                    ClassLoader owner,
                                    List<RemovedMapping> removedMappings)
            throws ReflectiveOperationException {
        var protocolRegistries = ((Map<Object, Object>) versions.get(packetRegistry)).values();
        var skippedMappings = 0;
        for (var protocolRegistry : protocolRegistries) {
            var classToId = (Map<Class<?>, Integer>) packetClassToId.get(protocolRegistry);
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
        }
        return skippedMappings;
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

    private record RemovedMapping(
            Map<Class<?>, Integer> classToId,
            Object suppliers,
            Class<?> packetClass,
            int packetId,
            Object supplier
    ) {
    }
}
