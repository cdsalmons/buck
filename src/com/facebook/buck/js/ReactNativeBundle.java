/*
 * Copyright 2015-present Facebook, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may
 *  not use this file except in compliance with the License. You may obtain
 *  a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations
 *  under the License.
 */

package com.facebook.buck.js;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.keys.AbiRule;
import com.facebook.buck.rules.AbstractBuildRule;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildTargetSourcePath;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.Sha1HashCode;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.shell.ShellStep;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.google.common.collect.ImmutableList;
import com.google.common.base.Optional;

import java.nio.file.Path;
import java.util.Arrays;

import javax.annotation.Nullable;

/**
 * Responsible for running the React Native JS packager in order to generate a single {@code .js}
 * bundle along with resources referenced by the javascript code.
 */
public class ReactNativeBundle extends AbstractBuildRule implements AbiRule {

  @AddToRuleKey
  private final SourcePath entryPath;

  @AddToRuleKey
  private final boolean isDevMode;

  @AddToRuleKey
  private final SourcePath jsPackager;

  @AddToRuleKey
  private final ReactNativePlatform platform;

  @AddToRuleKey
  private final String bundleName;

  @AddToRuleKey
  private final Optional<String> packagerFlags;

  private final ReactNativeDeps depsFinder;
  private final Path jsOutputDir;
  private final Path resource;

  protected ReactNativeBundle(
      BuildRuleParams ruleParams,
      SourcePathResolver resolver,
      SourcePath entryPath,
      boolean isDevMode,
      String bundleName,
      Optional<String> packagerFlags,
      SourcePath jsPackager,
      ReactNativePlatform platform,
      ReactNativeDeps depsFinder) {
    super(ruleParams, resolver);
    this.entryPath = entryPath;
    this.isDevMode = isDevMode;
    this.bundleName = bundleName;
    this.packagerFlags = packagerFlags;
    this.jsPackager = jsPackager;
    this.platform = platform;
    this.depsFinder = depsFinder;
    BuildTarget buildTarget = ruleParams.getBuildTarget();
    this.jsOutputDir = getPathToJSBundleDir(buildTarget);
    this.resource = getPathToResources(buildTarget);
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context,
      BuildableContext buildableContext) {
    ImmutableList.Builder<Step> steps = ImmutableList.builder();
    final Path jsOutput = jsOutputDir.resolve(bundleName);
    steps.add(new MakeCleanDirectoryStep(getProjectFilesystem(), jsOutput.getParent()));
    steps.add(new MakeCleanDirectoryStep(getProjectFilesystem(), resource));
    final Path sourceMapOutput = getPathToSourceMap(getBuildTarget());
    steps.add(new MakeCleanDirectoryStep(getProjectFilesystem(), sourceMapOutput.getParent()));

    steps.add(
        new ShellStep(getProjectFilesystem().getRootPath()) {
          @Override
          public String getShortName() {
            return "bundle_react_native";
          }

          @Override
          protected ImmutableList<String> getShellCommandInternal(ExecutionContext context) {
            return getBundleScript(
                getResolver().getAbsolutePath(jsPackager),
                getProjectFilesystem().resolve(getResolver().getAbsolutePath(entryPath)),
                platform,
                isDevMode,
                packagerFlags,
                getProjectFilesystem().resolve(jsOutput).toString(),
                getProjectFilesystem().resolve(resource).toString(),
                getProjectFilesystem().resolve(sourceMapOutput).toString());
          }
        });
    buildableContext.recordArtifact(jsOutputDir);
    buildableContext.recordArtifact(resource);
    buildableContext.recordArtifact(sourceMapOutput.getParent());
    return steps.build();
  }

  public SourcePath getJSBundleDir() {
    return new BuildTargetSourcePath(getBuildTarget(), jsOutputDir);
  }

  public Path getResources() {
    return resource;
  }

  public static Path getPathToJSBundleDir(BuildTarget target) {
    return BuildTargets.getGenPath(target, "__%s_js__/");
  }

  public static Path getPathToResources(BuildTarget target) {
    return BuildTargets.getGenPath(target, "__%s_res__/");
  }

  public static Path getPathToSourceMap(BuildTarget target) {
    return BuildTargets.getGenPath(target, "__%s_source_map__/source.map");
  }

  @Override
  @Nullable
  public Path getPathToOutput() {
    return null;
  }

  @Override
  public Sha1HashCode getAbiKeyForDeps() {
    return depsFinder.getInputsHash();
  }

  public static ImmutableList<String> getBundleScript(
      Path jsPackager,
      Path absoluteEntryPath,
      ReactNativePlatform platform,
      boolean isDevMode,
      Optional<String> packagerFlags,
      String absoluteBundleOutputPath,
      String absoluteResourceOutputPath,
      String absoluteSourceMapOutputPath) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();

    builder.add(
      jsPackager.toString(),
      "bundle",
      "--entry-file", absoluteEntryPath.toString(),
      "--platform", platform.toString(),
      "--dev", isDevMode ? "true" : "false",
      "--bundle-output", absoluteBundleOutputPath,
      "--assets-dest", absoluteResourceOutputPath,
      "--sourcemap-output", absoluteSourceMapOutputPath
    );

    if (packagerFlags.isPresent()) {
      builder.addAll(Arrays.asList(packagerFlags.get().split(" ")));
    }

    return builder.build();
  }
}
