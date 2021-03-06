/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.apple;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.test.selectors.TestSelectorList;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.util.FakeProcess;
import com.facebook.buck.util.FakeProcessExecutor;
import com.facebook.buck.util.ProcessExecutorParams;
import com.google.common.base.Optional;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

public class XctoolRunTestsStepTest {

  @Test
  public void xctoolCommandWithOnlyLogicTests() throws Exception {
    FakeProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    XctoolRunTestsStep step = new XctoolRunTestsStep(
        projectFilesystem,
        Paths.get("/path/to/xctool"),
        Optional.<Long>absent(),
        "iphonesimulator",
        Optional.<String>absent(),
        ImmutableSet.of(Paths.get("/path/to/Foo.xctest")),
        ImmutableMap.<Path, Path>of(),
        Paths.get("/path/to/output.json"),
        Optional.<XctoolRunTestsStep.StdoutReadingCallback>absent(),
        Suppliers.ofInstance(Optional.of(Paths.get("/path/to/developer/dir"))),
        TestSelectorList.EMPTY);
    ProcessExecutorParams xctoolParams =
        ProcessExecutorParams.builder()
            .setCommand(
                ImmutableList.of(
                    "/path/to/xctool",
                    "-reporter",
                    "json-stream",
                    "-sdk",
                    "iphonesimulator",
                    "run-tests",
                    "-logicTest",
                    "/path/to/Foo.xctest"))
            .setEnvironment(ImmutableMap.of("DEVELOPER_DIR", "/path/to/developer/dir"))
            .setDirectory(projectFilesystem.getRootPath().toAbsolutePath().toFile())
            .setRedirectOutput(ProcessBuilder.Redirect.PIPE)
            .build();
    FakeProcess fakeXctoolSuccess = new FakeProcess(0, "", "");
    FakeProcessExecutor processExecutor = new FakeProcessExecutor(
        ImmutableMap.of(xctoolParams, fakeXctoolSuccess));
    ExecutionContext executionContext = TestExecutionContext.newBuilder()
        .setProcessExecutor(processExecutor)
        .setEnvironment(ImmutableMap.<String, String>of())
        .build();
    assertThat(
        step.execute(executionContext),
        equalTo(0));
  }

  @Test
  public void xctoolCommandWithOnlyAppTests() throws Exception {
    FakeProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    XctoolRunTestsStep step = new XctoolRunTestsStep(
        projectFilesystem,
        Paths.get("/path/to/xctool"),
        Optional.<Long>absent(),
        "iphonesimulator",
        Optional.of("name=iPhone 5s"),
        ImmutableSet.<Path>of(),
        ImmutableMap.of(
            Paths.get("/path/to/FooAppTest.xctest"),
            Paths.get("/path/to/Foo.app")),
        Paths.get("/path/to/output.json"),
        Optional.<XctoolRunTestsStep.StdoutReadingCallback>absent(),
        Suppliers.ofInstance(Optional.of(Paths.get("/path/to/developer/dir"))),
        TestSelectorList.EMPTY);

    ProcessExecutorParams xctoolParams =
        ProcessExecutorParams.builder()
            .setCommand(
                ImmutableList.of(
                    "/path/to/xctool",
                    "-reporter",
                    "json-stream",
                    "-sdk",
                    "iphonesimulator",
                    "-destination",
                    "name=iPhone 5s",
                    "run-tests",
                    "-appTest",
                    "/path/to/FooAppTest.xctest:/path/to/Foo.app"))
            .setEnvironment(ImmutableMap.of("DEVELOPER_DIR", "/path/to/developer/dir"))
            .setDirectory(projectFilesystem.getRootPath().toAbsolutePath().toFile())
            .setRedirectOutput(ProcessBuilder.Redirect.PIPE)
            .build();
    FakeProcess fakeXctoolSuccess = new FakeProcess(0, "", "");
    FakeProcessExecutor processExecutor = new FakeProcessExecutor(
        ImmutableMap.of(xctoolParams, fakeXctoolSuccess));
    ExecutionContext executionContext = TestExecutionContext.newBuilder()
        .setProcessExecutor(processExecutor)
        .setEnvironment(ImmutableMap.<String, String>of())
        .build();
    assertThat(
        step.execute(executionContext),
        equalTo(0));
  }

  @Test
  public void xctoolCommandWithAppAndLogicTests() throws Exception {
    FakeProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    XctoolRunTestsStep step = new XctoolRunTestsStep(
        projectFilesystem,
        Paths.get("/path/to/xctool"),
        Optional.<Long>absent(),
        "iphonesimulator",
        Optional.of("name=iPhone 5s,OS=8.2"),
        ImmutableSet.of(
            Paths.get("/path/to/FooLogicTest.xctest")),
        ImmutableMap.of(
            Paths.get("/path/to/FooAppTest.xctest"),
            Paths.get("/path/to/Foo.app")),
        Paths.get("/path/to/output.json"),
        Optional.<XctoolRunTestsStep.StdoutReadingCallback>absent(),
        Suppliers.ofInstance(Optional.of(Paths.get("/path/to/developer/dir"))),
        TestSelectorList.EMPTY);

    ProcessExecutorParams xctoolParams =
        ProcessExecutorParams.builder()
            .setCommand(
                ImmutableList.of(
                    "/path/to/xctool",
                    "-reporter",
                    "json-stream",
                    "-sdk",
                    "iphonesimulator",
                    "-destination",
                    "name=iPhone 5s,OS=8.2",
                    "run-tests",
                    "-logicTest",
                    "/path/to/FooLogicTest.xctest",
                    "-appTest",
                    "/path/to/FooAppTest.xctest:/path/to/Foo.app"))
            .setEnvironment(ImmutableMap.of("DEVELOPER_DIR", "/path/to/developer/dir"))
            .setDirectory(projectFilesystem.getRootPath().toAbsolutePath().toFile())
            .setRedirectOutput(ProcessBuilder.Redirect.PIPE)
            .build();
    FakeProcess fakeXctoolSuccess = new FakeProcess(0, "", "");
    FakeProcessExecutor processExecutor = new FakeProcessExecutor(
        ImmutableMap.of(xctoolParams, fakeXctoolSuccess));
    ExecutionContext executionContext = TestExecutionContext.newBuilder()
        .setProcessExecutor(processExecutor)
        .setEnvironment(ImmutableMap.<String, String>of())
        .build();
    assertThat(
        step.execute(executionContext),
        equalTo(0));
  }

  @Test
  public void xctoolCommandWhichReturnsExitCode1DoesNotFailStep() throws Exception {
    FakeProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    XctoolRunTestsStep step = new XctoolRunTestsStep(
        projectFilesystem,
        Paths.get("/path/to/xctool"),
        Optional.<Long>absent(),
        "iphonesimulator",
        Optional.<String>absent(),
        ImmutableSet.of(Paths.get("/path/to/Foo.xctest")),
        ImmutableMap.<Path, Path>of(),
        Paths.get("/path/to/output.json"),
        Optional.<XctoolRunTestsStep.StdoutReadingCallback>absent(),
        Suppliers.ofInstance(Optional.of(Paths.get("/path/to/developer/dir"))),
        TestSelectorList.EMPTY);

    ProcessExecutorParams xctoolParams =
        ProcessExecutorParams.builder()
            .setCommand(
                ImmutableList.of(
                    "/path/to/xctool",
                    "-reporter",
                    "json-stream",
                    "-sdk",
                    "iphonesimulator",
                    "run-tests",
                    "-logicTest",
                    "/path/to/Foo.xctest"))
            .setEnvironment(ImmutableMap.of("DEVELOPER_DIR", "/path/to/developer/dir"))
            .setDirectory(projectFilesystem.getRootPath().toAbsolutePath().toFile())
            .setRedirectOutput(ProcessBuilder.Redirect.PIPE)
            .build();
    FakeProcess fakeXctoolSuccess = new FakeProcess(1, "", "");
    FakeProcessExecutor processExecutor = new FakeProcessExecutor(
        ImmutableMap.of(xctoolParams, fakeXctoolSuccess));
    ExecutionContext executionContext = TestExecutionContext.newBuilder()
        .setProcessExecutor(processExecutor)
        .setEnvironment(ImmutableMap.<String, String>of())
        .build();
    assertThat(
        step.execute(executionContext),
        equalTo(0));
  }

  @Test
  public void xctoolCommandWhichReturnsExitCode400FailsStep() throws Exception {
    FakeProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    XctoolRunTestsStep step = new XctoolRunTestsStep(
        projectFilesystem,
        Paths.get("/path/to/xctool"),
        Optional.<Long>absent(),
        "iphonesimulator",
        Optional.<String>absent(),
        ImmutableSet.of(Paths.get("/path/to/Foo.xctest")),
        ImmutableMap.<Path, Path>of(),
        Paths.get("/path/to/output.json"),
        Optional.<XctoolRunTestsStep.StdoutReadingCallback>absent(),
        Suppliers.ofInstance(Optional.of(Paths.get("/path/to/developer/dir"))),
        TestSelectorList.EMPTY);

    ProcessExecutorParams xctoolParams =
        ProcessExecutorParams.builder()
            .setCommand(
                ImmutableList.of(
                    "/path/to/xctool",
                    "-reporter",
                    "json-stream",
                    "-sdk",
                    "iphonesimulator",
                    "run-tests",
                    "-logicTest",
                    "/path/to/Foo.xctest"))
            .setEnvironment(ImmutableMap.of("DEVELOPER_DIR", "/path/to/developer/dir"))
            .setDirectory(projectFilesystem.getRootPath().toAbsolutePath().toFile())
            .setRedirectOutput(ProcessBuilder.Redirect.PIPE)
            .build();
    FakeProcess fakeXctoolSuccess = new FakeProcess(400, "", "");
    FakeProcessExecutor processExecutor = new FakeProcessExecutor(
        ImmutableMap.of(xctoolParams, fakeXctoolSuccess));
    ExecutionContext executionContext = TestExecutionContext.newBuilder()
        .setProcessExecutor(processExecutor)
        .setEnvironment(ImmutableMap.<String, String>of())
        .build();
    assertThat(
        step.execute(executionContext),
        not(equalTo(0)));
  }

  @Test
  public void xctoolCommandWithTestSelectorFiltersTests() throws Exception {
    FakeProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    XctoolRunTestsStep step = new XctoolRunTestsStep(
        projectFilesystem,
        Paths.get("/path/to/xctool"),
        Optional.<Long>absent(),
        "iphonesimulator",
        Optional.<String>absent(),
        ImmutableSet.of(Paths.get("/path/to/FooTest.xctest"), Paths.get("/path/to/BarTest.xctest")),
        ImmutableMap.<Path, Path>of(),
        Paths.get("/path/to/output.json"),
        Optional.<XctoolRunTestsStep.StdoutReadingCallback>absent(),
        Suppliers.ofInstance(Optional.of(Paths.get("/path/to/developer/dir"))),
        TestSelectorList.builder()
            .addRawSelectors("#.*Magic.*")
            .build());
    ProcessExecutorParams xctoolListOnlyParams =
        ProcessExecutorParams.builder()
            .setCommand(
                ImmutableList.of(
                    "/path/to/xctool",
                    "-reporter",
                    "json-stream",
                    "-sdk",
                    "iphonesimulator",
                    "run-tests",
                    "-logicTest",
                    "/path/to/FooTest.xctest",
                    "-logicTest",
                    "/path/to/BarTest.xctest",
                    "-listTestsOnly"))
            .setEnvironment(ImmutableMap.of("DEVELOPER_DIR", "/path/to/developer/dir"))
            .setDirectory(projectFilesystem.getRootPath().toAbsolutePath().toFile())
            .setRedirectOutput(ProcessBuilder.Redirect.PIPE)
            .build();
    try (InputStream stdout =
           getClass().getResourceAsStream("testdata/xctool-output/list-tests-only.json");
         InputStream stderr = new ByteArrayInputStream(new byte[0])) {
      assertThat(stdout, not(nullValue()));
      FakeProcess fakeXctoolListTestsProcess = new FakeProcess(
          0,
          ByteStreams.nullOutputStream(),
          stdout,
          stderr);
      ProcessExecutorParams xctoolRunTestsParamsWithOnlyFilters =
        ProcessExecutorParams.builder()
            .addCommand(
                "/path/to/xctool",
                "-reporter",
                "json-stream",
                "-sdk",
                "iphonesimulator",
                "run-tests",
                "-logicTest",
                "/path/to/FooTest.xctest",
                "-logicTest",
                "/path/to/BarTest.xctest",
                "-only",
                "/path/to/FooTest.xctest:FooTest/testMagicValue,FooTest/testAnotherMagicValue",
                "-only",
                "/path/to/BarTest.xctest:BarTest/testYetAnotherMagicValue")
            .setEnvironment(ImmutableMap.of("DEVELOPER_DIR", "/path/to/developer/dir"))
            .setDirectory(projectFilesystem.getRootPath().toAbsolutePath().toFile())
            .setRedirectOutput(ProcessBuilder.Redirect.PIPE)
            .build();
      FakeProcess fakeXctoolSuccess = new FakeProcess(0, "", "");
      FakeProcessExecutor processExecutor = new FakeProcessExecutor(
          ImmutableMap.of(
              xctoolListOnlyParams, fakeXctoolListTestsProcess,
              // The important part of this test is that we want to make sure xctool
              // is run with the correct -only parameters. (We don't really care what
              // the return value of this xctool is, so we make it always succeed.)
              xctoolRunTestsParamsWithOnlyFilters, fakeXctoolSuccess));
      ExecutionContext executionContext = TestExecutionContext.newBuilder()
          .setProcessExecutor(processExecutor)
          .setEnvironment(ImmutableMap.<String, String>of())
          .build();
      assertThat(
          step.execute(executionContext),
          equalTo(0));
    }
  }

  @Test
  public void xctoolCommandWithTestSelectorMatchingNothingFails() throws Exception {
    FakeProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    XctoolRunTestsStep step = new XctoolRunTestsStep(
        projectFilesystem,
        Paths.get("/path/to/xctool"),
        Optional.<Long>absent(),
        "iphonesimulator",
        Optional.<String>absent(),
        ImmutableSet.of(Paths.get("/path/to/FooTest.xctest"), Paths.get("/path/to/BarTest.xctest")),
        ImmutableMap.<Path, Path>of(),
        Paths.get("/path/to/output.json"),
        Optional.<XctoolRunTestsStep.StdoutReadingCallback>absent(),
        Suppliers.ofInstance(Optional.of(Paths.get("/path/to/developer/dir"))),
        TestSelectorList.builder()
            .addRawSelectors("Blargh#Xyzzy")
            .build());
    ProcessExecutorParams xctoolListOnlyParams =
        ProcessExecutorParams.builder()
            .setCommand(
                ImmutableList.of(
                    "/path/to/xctool",
                    "-reporter",
                    "json-stream",
                    "-sdk",
                    "iphonesimulator",
                    "run-tests",
                    "-logicTest",
                    "/path/to/FooTest.xctest",
                    "-logicTest",
                    "/path/to/BarTest.xctest",
                    "-listTestsOnly"))
            .setEnvironment(ImmutableMap.of("DEVELOPER_DIR", "/path/to/developer/dir"))
            .setDirectory(projectFilesystem.getRootPath().toAbsolutePath().toFile())
            .setRedirectOutput(ProcessBuilder.Redirect.PIPE)
            .build();
    try (InputStream stdout =
           getClass().getResourceAsStream("testdata/xctool-output/list-tests-only.json");
         InputStream stderr = new ByteArrayInputStream(new byte[0])) {
      assertThat(stdout, not(nullValue()));
      FakeProcess fakeXctoolListTestsProcess = new FakeProcess(
          0,
          ByteStreams.nullOutputStream(),
          stdout,
          stderr);
      FakeProcessExecutor processExecutor = new FakeProcessExecutor(
          ImmutableMap.of(
              xctoolListOnlyParams, fakeXctoolListTestsProcess));
      ExecutionContext executionContext = TestExecutionContext.newBuilder()
          .setProcessExecutor(processExecutor)
          .setEnvironment(ImmutableMap.<String, String>of())
          .build();
      assertThat(
          step.execute(executionContext),
          equalTo(1));
    }
  }
}
