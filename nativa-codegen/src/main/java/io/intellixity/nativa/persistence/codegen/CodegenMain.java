package io.intellixity.nativa.persistence.codegen;

import io.intellixity.nativa.persistence.authoring.EntityAuthoring;
import io.intellixity.nativa.persistence.authoring.InMemoryAuthoringRegistry;
import io.intellixity.nativa.persistence.authoring.yaml.YamlEntityAuthoringLoader;

import java.nio.file.*;
import java.util.*;

/**
 * CLI:
 *   CodegenMain <authoringDir> <generatedOutDir>
 */
public final class CodegenMain {
  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println("Usage: CodegenMain <authoringDir> <generatedOutDir>");
      System.exit(2);
    }

    Path authoringDir = Paths.get(args[0]);
    Path outDir = Paths.get(args[1]);

    YamlEntityAuthoringLoader loader = new YamlEntityAuthoringLoader();
    List<EntityAuthoring> entities = loader.loadDir(authoringDir);

    InMemoryAuthoringRegistry reg = new InMemoryAuthoringRegistry(entities);

    PojoGenerator pojoGen = new PojoGenerator(reg);
    RowReaderGenerator rrGen = new RowReaderGenerator(reg);
    PojoAccessorGenerator accGen = new PojoAccessorGenerator(reg);
    PojoMutatorGenerator mutGen = new PojoMutatorGenerator(reg);
    AccessorRegistryGenerator regGen = new AccessorRegistryGenerator(entities);
    MutatorRegistryGenerator mutRegGen = new MutatorRegistryGenerator(entities);

    for (EntityAuthoring ea : entities) {
      pojoGen.generatePojoIfNeeded(ea, outDir);
      rrGen.generateRowReader(ea, outDir);
      accGen.generateAccessorIfNeeded(ea, outDir);
      mutGen.generateMutatorIfNeeded(ea, outDir);
    }
    regGen.generate(outDir);
    mutRegGen.generate(outDir);

    System.out.println("Generated: " + entities.size() + " entities into: " + outDir);
  }
}

