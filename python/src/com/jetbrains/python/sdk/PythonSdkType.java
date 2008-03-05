package com.jetbrains.python.sdk;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.SdkVersionUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PythonFileType;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.TreeSet;

/**
 * @author yole
 */
public class PythonSdkType extends SdkType {
  public static PythonSdkType getInstance() {
    for (SdkType sdkType : Extensions.getExtensions(EP_NAME)) {
      if (sdkType instanceof PythonSdkType) {
        return (PythonSdkType)sdkType;
      }
    }
    assert false;
    return null;
  }

  public PythonSdkType() {
    super("Python SDK");
  }

  public Icon getIcon() {
    return PythonFileType.INSTANCE.getIcon();
  }

  public Icon getIconForAddAction() {
    return PythonFileType.INSTANCE.getIcon();
  }

  @Nullable
  public String suggestHomePath() {
    TreeSet<String> candidates = new TreeSet<String>();
    if (SystemInfo.isWindows) {
      VirtualFile rootDir = LocalFileSystem.getInstance().findFileByPath("C:\\");
      if (rootDir != null) {
        VirtualFile[] topLevelDirs = rootDir.getChildren();
        for(VirtualFile dir: topLevelDirs) {
          if (dir.isDirectory() && dir.getName().toLowerCase().startsWith("python")) {
            candidates.add(dir.getPath());
          }
        }
      }
    }
    else if (SystemInfo.isMac) {
      return "/Library/Frameworks/Python.framework/Versions/Current/";
    }

    if (candidates.size() > 0) {
      // return latest version
      String[] candidateArray = candidates.toArray(new String[candidates.size()]);
      return candidateArray [candidateArray.length-1];
    }
    return null;
  }

  public boolean isValidSdkHome(final String path) {
    return isPythonSdkHome(path) || isJythonSdkHome(path);
  }

  private static boolean isPythonSdkHome(final String path) {
    File f = getPythonBinaryPath(path);
    return f != null && f.exists();
  }

  private static boolean isJythonSdkHome(final String path) {
    File f = getJythonBinaryPath(path);
    return f != null && f.exists();
  }

  private static File getJythonBinaryPath(final String path) {
    if (SystemInfo.isWindows) {
      return new File(path, "jython.bat");
    }
    return null;
  }

  private static File getPythonBinaryPath(final String path) {
    if (SystemInfo.isWindows) {
      return new File(path, "python.exe");
    }
    else if (SystemInfo.isMac) {
      return new File(new File(path, "bin"), "python");
    }
    return null;
  }

  public String suggestSdkName(final String currentSdkName, final String sdkHome) {
    return getVersionString(sdkHome);
  }

  public AdditionalDataConfigurable createAdditionalDataConfigurable(final SdkModel sdkModel, final SdkModificator sdkModificator) {
    return null;
  }

  public void saveAdditionalData(final SdkAdditionalData additionalData, final Element additional) {
  }

  public String getPresentableName() {
    return "Python SDK";
  }

  public void setupSdkPaths(final Sdk sdk) {
    final SdkModificator sdkModificator = sdk.getSdkModificator();
    VirtualFile libDir = sdk.getHomeDirectory().findChild("Lib");
    if (libDir != null) {
      if (SystemInfo.isMac) {
        for (VirtualFile child : libDir.getChildren()) {
          if (child.getName().startsWith("python")) {
            sdkModificator.addRoot(child, OrderRootType.SOURCES);
            sdkModificator.addRoot(child, OrderRootType.CLASSES);
            break;
          }
        }
      }
      else {
        sdkModificator.addRoot(libDir, OrderRootType.SOURCES);
        sdkModificator.addRoot(libDir, OrderRootType.CLASSES);
      }
    }

    sdkModificator.commitChanges();
  }

  @Nullable
  public String getVersionString(final String sdkHome) {
    String binaryPath = getInterpreterPath(sdkHome);
    final boolean isJython = isJythonSdkHome(sdkHome);
    String marker = isJython ? "Jython" : "Python";
    String version = SdkVersionUtil.readVersionFromProcessOutput(sdkHome, new String[] { binaryPath, "--version" }, marker);
    if (version != null && isJython) {
      int p = version.indexOf(" on ");
      if (p >= 0) {
        return version.substring(0, p);
      }
    }
    return version;
  }

  public static String getInterpreterPath(final String sdkHome) {
    if (isJythonSdkHome(sdkHome)) {
      return getJythonBinaryPath(sdkHome).getPath();
    }
    return getPythonBinaryPath(sdkHome).getPath();
  }
}
