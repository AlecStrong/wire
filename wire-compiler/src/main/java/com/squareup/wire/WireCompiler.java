/*
 * Copyright 2013 Square Inc.
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
package com.squareup.wire;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import com.squareup.wire.java.JavaGenerator;
import com.squareup.wire.schema.EnumType;
import com.squareup.wire.schema.IdentifierSet;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.MessageType;
import com.squareup.wire.schema.ProtoFile;
import com.squareup.wire.schema.Schema;
import com.squareup.wire.schema.SchemaLoader;
import com.squareup.wire.schema.Type;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Command line interface to the Wire Java generator.
 *
 * <h3>Usage:</h3>
 *
 * <pre>
 * java WireCompiler --proto_path=&lt;path&gt; --java_out=&lt;path&gt;
 *     [--files=&lt;protos.include&gt;]
 *     [--includes=&lt;message_name&gt;[,&lt;message_name&gt;...]]
 *     [--excludes=&lt;message_name&gt;[,&lt;message_name&gt;...]]
 *     [--no_options]
 *     [--service_factory=&lt;class_name&gt;]
 *     [--service_factory_opt=&lt;value&gt;]
 *     [--service_factory_opt=&lt;value&gt;]...]
 *     [--quiet]
 *     [--dry_run]
 *     [--android]
 *     [--compact]
 *     [file [file...]]
 * </pre>
 *
 * <p>If the {@code --includes} flag is present, its argument must be a comma-separated list
 * of fully-qualified message or enum names. The output will be limited to those messages
 * and enums that are (transitive) dependencies of the listed names. The {@code --excludes} flag
 * excludes types, and takes precedence over {@code --includes}.
 *
 * <p>If the {@code --registry_class} flag is present, its argument must be a Java class name. A
 * class with the given name will be generated, containing a constant list of all extension
 * classes generated during the compile. This list is suitable for passing to Wire's constructor
 * at runtime for constructing its internal extension registry.
 *
 * <p>Unless the {@code --no_options} flag is supplied, code will be emitted for options on messages
 * and fields.  The presence of options on a message will result in a static member named
 * "MESSAGE_OPTIONS", initialized with the options and their values.   The presence of options on
 * a field (other than the standard options "default", "deprecated", and "packed") will result in
 * a static member named "FIELD_OPTIONS_&lt;field name&gt;" in the generated code, initialized
 * with the field option values.
 *
 * <p>If {@code --quiet} is specified, diagnostic messages to stdout are suppressed.
 *
 * <p>The {@code --dry_run} flag causes the compile to just emit the names of the source files that
 * would be generated to stdout.
 *
 * <p>The {@code --android} flag will cause all messages to implement the {@code Parcelable}
 * interface.
 *
 * <p>The {@code --compact} flag will emit code that uses reflection for reading, writing, and
 * toString methods which are normally implemented with code generation.
 */
public final class WireCompiler {
  public static final String PROTO_PATH_FLAG = "--proto_path=";
  public static final String JAVA_OUT_FLAG = "--java_out=";
  public static final String FILES_FLAG = "--files=";
  public static final String ROOTS_FLAG = "--roots="; // TODO(jwilson): drop before 2.0 final.
  public static final String INCLUDES_FLAG = "--includes=";
  public static final String EXCLUDES_FLAG = "--excludes=";
  public static final String NO_OPTIONS_FLAG = "--no_options";
  public static final String QUIET_FLAG = "--quiet";
  public static final String DRY_RUN_FLAG = "--dry_run";
  public static final String ANDROID = "--android";
  public static final String COMPACT = "--compact";

  private static final String CODE_GENERATED_BY_WIRE =
      "Code generated by Wire protocol buffer compiler, do not edit.";

  private final FileSystem fs;
  private final WireLogger log;

  final List<String> protoPaths;
  final String javaOut;
  final List<String> sourceFileNames;
  final IdentifierSet identifierSet;
  final boolean emitOptions;
  final boolean dryRun;
  final boolean emitAndroid;
  final boolean emitCompact;

  WireCompiler(FileSystem fs, WireLogger log, List<String> protoPaths, String javaOut,
      List<String> sourceFileNames, IdentifierSet identifierSet, boolean emitOptions,
      boolean dryRun, boolean emitAndroid, boolean emitCompact) {
    this.fs = fs;
    this.log = log;
    this.protoPaths = protoPaths;
    this.javaOut = javaOut;
    this.sourceFileNames = sourceFileNames;
    this.identifierSet = identifierSet;
    this.emitOptions = emitOptions;
    this.dryRun = dryRun;
    this.emitAndroid = emitAndroid;
    this.emitCompact = emitCompact;
  }

  public static void main(String... args) throws IOException {
    try {
      WireCompiler wireCompiler = forArgs(args);
      wireCompiler.compile();
    } catch (WireException e) {
      System.err.print("Fatal: ");
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }

  static WireCompiler forArgs(String... args) throws WireException {
    return forArgs(FileSystems.getDefault(), new ConsoleWireLogger(), args);
  }

  static WireCompiler forArgs(
      FileSystem fileSystem, WireLogger logger, String... args) throws WireException {
    List<String> sourceFileNames = new ArrayList<>();
    IdentifierSet.Builder identifierSetBuilder = new IdentifierSet.Builder();
    boolean emitOptions = true;
    List<String> protoPaths = new ArrayList<>();
    String javaOut = null;
    boolean quiet = false;
    boolean dryRun = false;
    boolean emitAndroid = false;
    boolean emitCompact = false;

    for (String arg : args) {
      if (arg.startsWith(PROTO_PATH_FLAG)) {
        protoPaths.add(arg.substring(PROTO_PATH_FLAG.length()));
      } else if (arg.startsWith(JAVA_OUT_FLAG)) {
        javaOut = arg.substring(JAVA_OUT_FLAG.length());
      } else if (arg.startsWith(FILES_FLAG)) {
        File files = new File(arg.substring(FILES_FLAG.length()));
        String[] fileNames;
        try {
          fileNames = new Scanner(files, "UTF-8").useDelimiter("\\A").next().split("\n");
        } catch (FileNotFoundException ex) {
          throw new WireException("Error processing argument " + arg, ex);
        }
        sourceFileNames.addAll(Arrays.asList(fileNames));
      } else if (arg.startsWith(INCLUDES_FLAG) || arg.startsWith(ROOTS_FLAG)) {
        String prefix = arg.startsWith(INCLUDES_FLAG) ? INCLUDES_FLAG : ROOTS_FLAG;
        for (String identifier : splitArg(arg, prefix.length())) {
          identifierSetBuilder.include(identifier);
        }
      } else if (arg.startsWith(EXCLUDES_FLAG)) {
        for (String identifier : splitArg(arg, EXCLUDES_FLAG.length())) {
          identifierSetBuilder.exclude(identifier);
        }
      } else if (arg.equals(NO_OPTIONS_FLAG)) {
        emitOptions = false;
      } else if (arg.equals(QUIET_FLAG)) {
        quiet = true;
      } else if (arg.equals(DRY_RUN_FLAG)) {
        dryRun = true;
      } else if (arg.equals(ANDROID)) {
        emitAndroid = true;
      } else if (arg.equals(COMPACT)) {
        emitCompact = true;
      } else if (arg.startsWith("--")) {
        throw new IllegalArgumentException("Unknown argument '" + arg + "'.");
      } else {
        sourceFileNames.add(arg);
      }
    }

    if (javaOut == null) {
      throw new WireException("Must specify " + JAVA_OUT_FLAG + " flag");
    }

    logger.setQuiet(quiet);

    return new WireCompiler(fileSystem, logger, protoPaths, javaOut, sourceFileNames,
        identifierSetBuilder.build(), emitOptions, dryRun, emitAndroid, emitCompact);
  }

  private static List<String> splitArg(String arg, int flagLength) {
    return Arrays.asList(arg.substring(flagLength).split(","));
  }

  void compile() throws IOException {
    SchemaLoader schemaLoader = new SchemaLoader();
    for (String protoPath : protoPaths) {
      schemaLoader.addSource(fs.getPath(protoPath));
    }
    for (String sourceFileName : sourceFileNames) {
      schemaLoader.addProto(sourceFileName);
    }
    Schema schema = schemaLoader.load();

    if (!identifierSet.isEmpty()) {
      log.info("Analyzing dependencies of root types.");
      schema = schema.prune(identifierSet);
      for (String rule : identifierSet.unusedIncludes()) {
        log.info("Unused include: " + rule);
      }
      for (String rule : identifierSet.unusedExcludes()) {
        log.info("Unused exclude: " + rule);
      }
    }

    JavaGenerator javaGenerator = JavaGenerator.get(schema)
        .withOptions(emitOptions)
        .withAndroid(emitAndroid)
        .withCompact(emitCompact);

    for (ProtoFile protoFile : schema.protoFiles()) {
      if (!sourceFileNames.isEmpty() && !sourceFileNames.contains(protoFile.location().path())) {
        continue; // Don't emit anything for files not explicitly compiled.
      }

      for (Type type : protoFile.types()) {
        ClassName javaTypeName = (ClassName) javaGenerator.typeName(type.name());
        TypeSpec typeSpec = type instanceof MessageType
            ? javaGenerator.generateMessage((MessageType) type)
            : javaGenerator.generateEnum((EnumType) type);
        writeJavaFile(javaTypeName, typeSpec, type.location());
      }
    }
  }

  private void writeJavaFile(ClassName javaTypeName, TypeSpec typeSpec, Location location)
      throws IOException {
    JavaFile.Builder builder = JavaFile.builder(javaTypeName.packageName(), typeSpec)
        .addFileComment("$L", CODE_GENERATED_BY_WIRE);
    if (location != null) {
      builder.addFileComment("\nSource file: $L", location.withoutBase());
    }
    JavaFile javaFile = builder.build();

    Path path = fs.getPath(javaOut);
    log.artifact(path, javaFile);

    try {
      if (!dryRun) {
        javaFile.writeTo(path);
      }
    } catch (IOException e) {
      throw new IOException("Error emitting " + javaFile.packageName + "."
          + javaFile.typeSpec.name + " to " + javaOut, e);
    }
  }
}
