package core.com.rylinaux.plugman.plugins;

import java.io.File;
import java.util.List;

public interface Plugin {

    String getName();

    boolean isEnabled();

    String getVersion();

    List<String> getDepend();

    List<String> getSoftDepend();

    List<String> getAuthors();

    File getFile();

    <T> T getHandle();
}
