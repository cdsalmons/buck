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

package com.facebook.buck.cxx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.AbstractBuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.FakeBuildRuleParamsBuilder;
import com.facebook.buck.rules.HashedFileTool;
import com.facebook.buck.rules.RuleKey;
import com.facebook.buck.rules.RuleKeyBuilder;
import com.facebook.buck.rules.RuleKeyBuilderFactory;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.TestSourcePath;
import com.facebook.buck.rules.keys.DefaultRuleKeyBuilderFactory;
import com.facebook.buck.testutil.FakeFileHashCache;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.rules.args.SanitizedArg;
import com.facebook.buck.rules.args.SourcePathArg;
import com.facebook.buck.rules.args.StringArg;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CxxLinkTest {

  private static final Linker DEFAULT_LINKER = new GnuLinker(new HashedFileTool(Paths.get("ld")));
  private static final Path DEFAULT_OUTPUT = Paths.get("test.exe");
  private static final ImmutableList<Arg> DEFAULT_ARGS =
      ImmutableList.of(
          new StringArg("-rpath"),
          new StringArg("/lib"),
          new StringArg("libc.a"),
          new SourcePathArg(
              new SourcePathResolver(new BuildRuleResolver()),
              new TestSourcePath("a.o")),
          new SourcePathArg(
              new SourcePathResolver(new BuildRuleResolver()),
              new TestSourcePath("b.o")),
          new SourcePathArg(
              new SourcePathResolver(new BuildRuleResolver()),
              new TestSourcePath("libc.a")));
  private static final ImmutableSet<Path> DEFAULT_LIBRARIES = ImmutableSet.of(
      Paths.get("/System/Libraries/libz.dynlib"));
  private static final DebugPathSanitizer DEFAULT_SANITIZER =
      CxxPlatforms.DEFAULT_DEBUG_PATH_SANITIZER;

  private RuleKey generateRuleKey(
      RuleKeyBuilderFactory factory,
      AbstractBuildRule rule) {

    RuleKeyBuilder builder = factory.newInstance(rule);
    return builder.build();
  }

  @Test
  public void testThatInputChangesCauseRuleKeyChanges() {
    SourcePathResolver pathResolver = new SourcePathResolver(new BuildRuleResolver());
    BuildTarget target = BuildTargetFactory.newInstance("//foo:bar");
    BuildRuleParams params = new FakeBuildRuleParamsBuilder(target).build();
    RuleKeyBuilderFactory ruleKeyBuilderFactory =
        new DefaultRuleKeyBuilderFactory(
            FakeFileHashCache.createFromStrings(
                ImmutableMap.of(
                    "ld", Strings.repeat("0", 40),
                    "a.o", Strings.repeat("a", 40),
                    "b.o", Strings.repeat("b", 40),
                    "libc.a", Strings.repeat("c", 40),
                    "different", Strings.repeat("d", 40))),
            pathResolver);

    // Generate a rule key for the defaults.
    RuleKey defaultRuleKey = generateRuleKey(
        ruleKeyBuilderFactory,
        new CxxLink(
            params,
            pathResolver,
            DEFAULT_LINKER,
            DEFAULT_OUTPUT,
            DEFAULT_ARGS,
            DEFAULT_LIBRARIES,
            DEFAULT_SANITIZER));

    // Verify that changing the archiver causes a rulekey change.
    RuleKey linkerChange = generateRuleKey(
        ruleKeyBuilderFactory,
        new CxxLink(
            params,
            pathResolver,
            new GnuLinker(new HashedFileTool(Paths.get("different"))),
            DEFAULT_OUTPUT,
            DEFAULT_ARGS,
            DEFAULT_LIBRARIES,
            DEFAULT_SANITIZER));
    assertNotEquals(defaultRuleKey, linkerChange);

    // Verify that changing the output path causes a rulekey change.
    RuleKey outputChange = generateRuleKey(
        ruleKeyBuilderFactory,
        new CxxLink(
            params,
            pathResolver,
            DEFAULT_LINKER,
            Paths.get("different"),
            DEFAULT_ARGS,
            DEFAULT_LIBRARIES,
            DEFAULT_SANITIZER));
    assertNotEquals(defaultRuleKey, outputChange);

    // Verify that changing the flags causes a rulekey change.
    RuleKey flagsChange = generateRuleKey(
        ruleKeyBuilderFactory,
        new CxxLink(
            params,
            pathResolver,
            DEFAULT_LINKER,
            DEFAULT_OUTPUT,
            ImmutableList.<Arg>of(
                new SourcePathArg(
                    new SourcePathResolver(new BuildRuleResolver()),
                    new TestSourcePath("different"))),
            DEFAULT_LIBRARIES,
            DEFAULT_SANITIZER));
    assertNotEquals(defaultRuleKey, flagsChange);

    // Verify that changing the libraries causes a rulekey change.
    RuleKey librariesRootsChange = generateRuleKey(
        ruleKeyBuilderFactory,
        new CxxLink(
            params,
            pathResolver,
            DEFAULT_LINKER,
            DEFAULT_OUTPUT,
            DEFAULT_ARGS,
            ImmutableSet.of(Paths.get("/System/Libraries/libx.dynlib")),
            DEFAULT_SANITIZER));
    assertNotEquals(defaultRuleKey, librariesRootsChange);

  }

  @Test
  public void sanitizedPathsInFlagsDoNotAffectRuleKey() {
    SourcePathResolver pathResolver = new SourcePathResolver(new BuildRuleResolver());
    BuildTarget target = BuildTargetFactory.newInstance("//foo:bar");
    BuildRuleParams params = new FakeBuildRuleParamsBuilder(target).build();
    RuleKeyBuilderFactory ruleKeyBuilderFactory =
        new DefaultRuleKeyBuilderFactory(
            FakeFileHashCache.createFromStrings(
                ImmutableMap.of(
                    "ld", Strings.repeat("0", 40),
                    "a.o", Strings.repeat("a", 40),
                    "b.o", Strings.repeat("b", 40),
                    "libc.a", Strings.repeat("c", 40),
                    "different", Strings.repeat("d", 40))),
            pathResolver);

    // Set up a map to sanitize the differences in the flags.
    int pathSize = 10;
    DebugPathSanitizer sanitizer1 =
        new DebugPathSanitizer(
            pathSize,
            File.separatorChar,
            Paths.get("PWD"),
            ImmutableBiMap.of(Paths.get("something"), Paths.get("A")));
    DebugPathSanitizer sanitizer2 =
        new DebugPathSanitizer(
            pathSize,
            File.separatorChar,
            Paths.get("PWD"),
            ImmutableBiMap.of(Paths.get("different"), Paths.get("A")));

    // Generate a rule with a path we need to sanitize to a consistent value.
    ImmutableList<Arg> args1 =
        ImmutableList.<Arg>of(
            new SanitizedArg(
                sanitizer1.sanitize(Optional.<Path>absent()),
                "-Lsomething/foo"));
    RuleKey ruleKey1 = generateRuleKey(
        ruleKeyBuilderFactory,
        new CxxLink(
            params,
            pathResolver,
            DEFAULT_LINKER,
            DEFAULT_OUTPUT,
            args1,
            DEFAULT_LIBRARIES,
            sanitizer1));

    // Generate another rule with a different path we need to sanitize to the
    // same consistent value as above.
    ImmutableList<Arg> args2 =
        ImmutableList.<Arg>of(
            new SanitizedArg(
                sanitizer2.sanitize(Optional.<Path>absent()),
                "-Ldifferent/foo"));
    RuleKey ruleKey2 = generateRuleKey(
        ruleKeyBuilderFactory,
        new CxxLink(
            params,
            pathResolver,
            DEFAULT_LINKER,
            DEFAULT_OUTPUT,
            args2,
            DEFAULT_LIBRARIES,
            sanitizer2));

    assertEquals(ruleKey1, ruleKey2);
  }

}
