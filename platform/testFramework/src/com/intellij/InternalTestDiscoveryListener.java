/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestListener;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Used in TestAll to collect data in command line
 */
@SuppressWarnings("unused")
public class InternalTestDiscoveryListener implements TestListener, Closeable {
  private final String myModuleName;
  private final String myTracesDirectory;
  private List<String> myCompletedMethodNames = new ArrayList<>();
  private final Alarm myProcessTracesAlarm;
  private Object myDiscoveryIndex;
  private Class<?> myDiscoveryIndexClass;

  public InternalTestDiscoveryListener() {
    myProcessTracesAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, null);
    myTracesDirectory = System.getProperty("org.jetbrains.instrumentation.trace.dir");
    myModuleName = System.getProperty("org.jetbrains.instrumentation.main.module");
  }

  private Object getIndex() {
    if (myDiscoveryIndex == null) {
      final Project project = ProjectManager.getInstance().getDefaultProject();
      try {
        myDiscoveryIndexClass = Class.forName("com.intellij.execution.testDiscovery.TestDiscoveryIndex");
        myDiscoveryIndex = myDiscoveryIndexClass
          .getConstructor(Project.class, String.class)
          .newInstance(project, myTracesDirectory);
      }
      catch (Throwable e) {
        e.printStackTrace();
      }
    }
    return myDiscoveryIndex;
  }

  @Override
  public void addError(Test test, Throwable t) {}

  @Override
  public void addFailure(Test test, AssertionFailedError t) {}

  @Override
  public void endTest(Test test) {
    final String className = getClassName(test);
    final String methodName = getMethodName(test);

    try {
      final Object data = getData();
      Method testEnded = data.getClass().getMethod("testDiscoveryEnded", new Class[] {String.class});
      testEnded.invoke(data, new Object[] {"j" + className + "-" + methodName});
    } catch (Throwable t) {
      t.printStackTrace();
    }

    myCompletedMethodNames.add("j" + className + "." + methodName);

    if (myCompletedMethodNames.size() > 50) {
      flushCurrentTraces();
    }
  }

  protected void flushCurrentTraces() {
    final String[] fullTestNames = ArrayUtil.toStringArray(myCompletedMethodNames);
    myCompletedMethodNames.clear();
    myProcessTracesAlarm.addRequest(() -> {
      try {
        final Method method = Class.forName("com.intellij.execution.testDiscovery.TestDiscoveryExtension")
          .getMethod("processAvailableTraces", fullTestNames.getClass(), myTracesDirectory.getClass(), String.class, String.class, myDiscoveryIndexClass);
        method.invoke(null, fullTestNames, myTracesDirectory, myModuleName, "j", getIndex());
      }
      catch (Throwable e) {
        e.printStackTrace();
      }
    }, 100);
  }

  private static String getMethodName(Test test) {
    final String toString = test.toString();
    final int braceIdx = toString.indexOf("(");
    return braceIdx > 0 ? toString.substring(0, braceIdx) : toString;
  }

  private static String getClassName(Test test) {
    final String toString = test.toString();
    final int braceIdx = toString.indexOf("(");
    return braceIdx > 0 && toString.endsWith(")") ? toString.substring(braceIdx + 1, toString.length() - 1) : null;
  }

  @Override
  public void startTest(Test test) {
    try {
      final Object data = getData();
      Method testStarted = data.getClass().getMethod("testDiscoveryStarted", new Class[] {String.class});
      testStarted.invoke(data, new Object[] {getClassName(test) + "-" + getMethodName(test)});
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  protected Object getData() throws Exception {
    return Class.forName("com.intellij.rt.coverage.data.ProjectData")
      .getMethod("getProjectData", new Class[0])
      .invoke(null, new Object[0]);
  }

  @Override
  public void close() throws IOException {
    myProcessTracesAlarm.cancelAllRequests();
    myProcessTracesAlarm.addRequest(() -> {
     flushCurrentTraces();
      Disposer.dispose(myProcessTracesAlarm);
    }, 0);
  }
}
