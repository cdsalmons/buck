/*
 * Copyright 2015-present Facebook, Inc.
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

package com.facebook.buck.parser;


import static com.facebook.buck.parser.ParserConfig.DEFAULT_BUILD_FILE_NAME;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.facebook.buck.cli.FakeBuckConfig;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.BuckEventBusFactory;
import com.facebook.buck.event.ConsoleEvent;
import com.facebook.buck.io.ExecutableFinder;
import com.facebook.buck.json.BuildFileParseException;
import com.facebook.buck.json.ProjectBuildFileParser;
import com.facebook.buck.json.ProjectBuildFileParserFactory;
import com.facebook.buck.json.ProjectBuildFileParserOptions;
import com.facebook.buck.python.PythonBuckConfig;
import com.facebook.buck.rules.Cell;
import com.facebook.buck.rules.KnownBuildRuleTypes;
import com.facebook.buck.rules.TestCellBuilder;
import com.facebook.buck.testutil.TestConsole;
import com.facebook.buck.timing.FakeClock;
import com.facebook.buck.util.Console;
import com.facebook.buck.util.FakeProcess;
import com.facebook.buck.util.FakeProcessExecutor;
import com.facebook.buck.util.ProcessExecutor;
import com.facebook.buck.util.ProcessExecutorParams;
import com.facebook.buck.util.environment.Platform;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.Subscribe;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ProjectBuildFileParserTest {

  private Cell cell;

  @Before
  public void createCell() throws IOException, InterruptedException {
    cell = new TestCellBuilder().build();
  }

  @Test
  public void whenSubprocessReturnsSuccessThenProjectBuildFileParserClosesCleanly()
      throws IOException, BuildFileParseException, InterruptedException {
    TestProjectBuildFileParserFactory buildFileParserFactory =
        new TestProjectBuildFileParserFactory(cell.getRoot(), cell.getKnownBuildRuleTypes());
    try (ProjectBuildFileParser buildFileParser =
             buildFileParserFactory.createNoopParserThatAlwaysReturnsSuccess()) {
      buildFileParser.initIfNeeded();
      // close() is called implicitly at the end of this block. It must not throw.
    }
  }


  @Test(expected = BuildFileParseException.class)
  public void whenSubprocessReturnsFailureThenProjectBuildFileParserThrowsOnClose()
      throws IOException, BuildFileParseException, InterruptedException {
    TestProjectBuildFileParserFactory buildFileParserFactory =
        new TestProjectBuildFileParserFactory(cell.getRoot(), cell.getKnownBuildRuleTypes());
    try (ProjectBuildFileParser buildFileParser =
             buildFileParserFactory.createNoopParserThatAlwaysReturnsError()) {
      buildFileParser.initIfNeeded();
      // close() is called implicitly at the end of this block. It must throw.
    }
  }

  @Test
  public void whenSubprocessRaisesWarningThenConsoleEventPublished()
      throws IOException, BuildFileParseException, InterruptedException {
    // This test depends on unix utilities that don't exist on Windows.
    assumeTrue(Platform.detect() != Platform.WINDOWS);

    TestProjectBuildFileParserFactory buildFileParserFactory =
        new TestProjectBuildFileParserFactory(cell.getRoot(), cell.getKnownBuildRuleTypes());
    BuckEventBus buckEventBus = BuckEventBusFactory.newInstance(new FakeClock(0));
    final List<ConsoleEvent> consoleEvents = new ArrayList<>();
    class EventListener {
      @Subscribe
      public void onConsoleEvent(ConsoleEvent consoleEvent) {
        consoleEvents.add(consoleEvent);
      }
    }
    EventListener eventListener = new EventListener();
    buckEventBus.register(eventListener);
    try (ProjectBuildFileParser buildFileParser =
             buildFileParserFactory.createNoopParserThatAlwaysReturnsSuccessAndPrintsWarning(
                 buckEventBus)) {
      buildFileParser.initIfNeeded();
    }
    assertThat(
        consoleEvents,
        Matchers.contains(
            Matchers.hasToString("Warning raised by BUCK file parser: Don't Panic!")));
  }
  /**
   * ProjectBuildFileParser test double which counts the number of times rules are parsed to test
   * caching logic in Parser.
   */
  private static class TestProjectBuildFileParserFactory implements ProjectBuildFileParserFactory {
    private final Path projectRoot;
    private final KnownBuildRuleTypes buildRuleTypes;

    public TestProjectBuildFileParserFactory(
        Path projectRoot,
        KnownBuildRuleTypes buildRuleTypes) {
      this.projectRoot = projectRoot;
      this.buildRuleTypes = buildRuleTypes;
    }

    @Override
    public ProjectBuildFileParser createParser(
        Console console,
        ImmutableMap<String, String> environment,
        BuckEventBus buckEventBus) {
      PythonBuckConfig config = new PythonBuckConfig(
          FakeBuckConfig.builder().setEnvironment(environment).build(),
          new ExecutableFinder());
      return new TestProjectBuildFileParser(
          config.getPythonInterpreter(),
          new ProcessExecutor(console));
    }

    public ProjectBuildFileParser createNoopParserThatAlwaysReturnsError() {
      return new TestProjectBuildFileParser(
          "fake-python",
          new FakeProcessExecutor(
              new Function<ProcessExecutorParams, FakeProcess>() {
                @Override
                public FakeProcess apply(ProcessExecutorParams params) {
                  return new FakeProcess(1, "JSON\n", "");
                }
              },
              new TestConsole()));
    }

    public ProjectBuildFileParser createNoopParserThatAlwaysReturnsSuccess() {
      return new TestProjectBuildFileParser(
          "fake-python",
          new FakeProcessExecutor(
              new Function<ProcessExecutorParams, FakeProcess>() {
                @Override
                public FakeProcess apply(ProcessExecutorParams params) {
                  return new FakeProcess(0, "JSON\n", "");
                }
              },
              new TestConsole()));
    }

    public ProjectBuildFileParser createNoopParserThatAlwaysReturnsSuccessAndPrintsWarning(
        BuckEventBus buckEventBus) {
      return new TestProjectBuildFileParser(
          "fake-python",
          new FakeProcessExecutor(
              new Function<ProcessExecutorParams, FakeProcess>() {
                @Override
                public FakeProcess apply(ProcessExecutorParams params) {
                  return new FakeProcess(0, "JSON\n", "Don't Panic!");
                }
              },
              new TestConsole()),
          buckEventBus);
    }

    private class TestProjectBuildFileParser extends ProjectBuildFileParser {
      public TestProjectBuildFileParser(
          String pythonInterpreter,
          ProcessExecutor processExecutor) {
        this(pythonInterpreter, processExecutor, BuckEventBusFactory.newInstance());
      }
      public TestProjectBuildFileParser(
          String pythonInterpreter,
          ProcessExecutor processExecutor,
          BuckEventBus buckEventBus) {
        super(
            ProjectBuildFileParserOptions.builder()
                .setProjectRoot(projectRoot)
                .setPythonInterpreter(pythonInterpreter)
                .setAllowEmptyGlobs(ParserConfig.DEFAULT_ALLOW_EMPTY_GLOBS)
                .setBuildFileName(DEFAULT_BUILD_FILE_NAME)
                .setDefaultIncludes(ImmutableSet.of("//java/com/facebook/defaultIncludeFile"))
                .setDescriptions(buildRuleTypes.getAllDescriptions())
                .build(),
            ImmutableMap.<String, String>of(),
            buckEventBus,
            processExecutor);
      }
    }
  }
}
