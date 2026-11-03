import java.nio.file.Path;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.validation.DirectoryValidator;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.ChunkPos;

public class ValidatePack {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Path pack = Path.of(args[0]).toAbsolutePath();
        var repository = ServerPacksSource.createPackRepository(pack.getParent(), new DirectoryValidator(p -> false));
        repository.reload();
        String packId = "file/" + pack.getFileName();
        if (!repository.isAvailable(packId)) throw new AssertionError("Pack not discovered: " + repository.getAvailableIds());
        // Do not auto-enable older packaged builds next to the folder under test.
        var disabled = repository.getAvailableIds().stream().filter(id -> !id.equals("vanilla") && !id.equals(packId)).toList();
        var data = new WorldDataConfiguration(new DataPackConfig(List.of("vanilla", packId), disabled), FeatureFlags.DEFAULT_FLAGS);
        var config = new WorldLoader.InitConfig(new WorldLoader.PackConfig(repository, data, false, false), Commands.CommandSelection.ALL, PermissionSet.ALL_PERMISSIONS);
        WorldLoader.load(config,
            context -> new WorldLoader.DataLoadOutput<>(Boolean.TRUE, context.datapackDimensions()),
            (resources, server, layers, cookie) -> {
                var registries = layers.compositeAccess();
                try (var storage = LevelStorageSource.createDefault(Path.of("campers-cache/testing/generation-worlds")).createAccess("flat-generation-check")) {
                    var manager = new StructureTemplateManager(resources, storage, DataFixers.getDataFixer(), registries.lookupOrThrow(Registries.BLOCK));
                    var generator = new FlatLevelSource(FlatLevelGeneratorSettings.getDefault(registries.lookupOrThrow(Registries.BIOME), registries.lookupOrThrow(Registries.STRUCTURE_SET), registries.lookupOrThrow(Registries.PLACED_FEATURE)));
                    for (String name : List.of("camp", "supply_shack", "hidden_storehouse")) {
                        var structure = registries.lookupOrThrow(Registries.STRUCTURE).getValue(Identifier.parse("campers_cache:" + name));
                        var context = new Structure.GenerationContext(registries, generator, generator.getBiomeSource(), null, null, manager, 221103L, new ChunkPos(0, 0), LevelHeightAccessor.create(-64, 384), biome -> true);
                        var stub = structure.findValidGenerationPoint(context).orElseThrow(() -> new AssertionError("No generation point: " + name));
                        var pieces = stub.getPiecesBuilder().build().pieces();
                        if (pieces.size() != 1) throw new AssertionError("Expected one building piece, got " + pieces.size() + ": " + name);
                        System.out.println("PASS actual structure-piece generation: " + name + " pieces=" + pieces.size());
                    }
                } catch (java.io.IOException e) { throw new RuntimeException(e); }
                for (String name : List.of("camp", "supply_shack", "hidden_storehouse")) {
                    var id = Identifier.parse("campers_cache:" + name);
                    if (!registries.lookupOrThrow(Registries.STRUCTURE).containsKey(id)) throw new AssertionError("Missing structure " + id);
                    if (!registries.lookupOrThrow(Registries.STRUCTURE_SET).containsKey(id)) throw new AssertionError("Missing structure set " + id);
                    if (!registries.lookupOrThrow(Registries.TEMPLATE_POOL).containsKey(id)) throw new AssertionError("Missing template pool " + id);
                    try {
                        var nbt = NbtIo.readCompressed(pack.resolve("data/campers_cache/structure/" + name + ".nbt"), NbtAccounter.unlimitedHeap());
                        var template = new StructureTemplate();
                        template.load(registries.lookupOrThrow(Registries.BLOCK), nbt);
                        System.out.println("PASS template and worldgen registries: " + id + " " + template.getSize());
                    } catch (Exception e) { throw new RuntimeException(e); }
                }
                for (String name : List.of("test_all", "info")) {
                    if (server.getFunctionLibrary().getFunction(Identifier.parse("campers_cache:" + name)).isEmpty()) throw new AssertionError("Function did not compile: " + name);
                }
                // Exercise Minecraft's real ore-rule processor, without creating a world.
                var processor = registries.lookupOrThrow(Registries.PROCESSOR_LIST)
                    .getValue(Identifier.parse("campers_cache:storehouse_ores")).list().getFirst();
                var level = (ServerLevelAccessor) Proxy.newProxyInstance(ValidatePack.class.getClassLoader(), new Class<?>[]{ServerLevelAccessor.class},
                    (proxy, method, values) -> {
                        if (method.getName().equals("getBlockState")) return Blocks.AIR.defaultBlockState();
                        throw new UnsupportedOperationException("Unexpected world access: " + method.getName());
                    });
                Map<String,Integer> counts = new TreeMap<>();
                for (int i = 0; i < 10000; i++) {
                    var source = new StructureTemplate.StructureBlockInfo(new BlockPos(i, 64, 17), registries.lookupOrThrow(Registries.BLOCK).getValue(Identifier.parse("minecraft:pink_wool")).defaultBlockState(), null);
                    var result = processor.processBlock(level, BlockPos.ZERO, BlockPos.ZERO, source.pos(), source, new StructurePlaceSettings());
                    String block = registries.lookupOrThrow(Registries.BLOCK).getKey(result.state().getBlock()).toString();
                    if (!block.startsWith("minecraft:deepslate_") || !block.endsWith("_ore")) throw new AssertionError("Unreplaced marker: " + block);
                    counts.merge(block, 1, Integer::sum);
                }
                for (String ore : List.of("iron", "coal", "copper", "diamond", "emerald")) {
                    double expected = ore.equals("diamond") || ore.equals("emerald") ? 0.30 : ore.equals("iron") ? 0.14 : 0.13;
                    double actual = counts.getOrDefault("minecraft:deepslate_" + ore + "_ore", 0) / 10000.0;
                    if (Math.abs(actual - expected) > 0.025) throw new AssertionError("Unexpected ore distribution: " + counts);
                }
                System.out.println("PASS actual Minecraft ore processor, 10000 positions: " + counts);
                resources.close();
                System.out.println("PASS datapack resource reload and command-function compilation");
                return Boolean.TRUE;
            }, Runnable::run, Runnable::run).join();
        System.exit(0);
    }
}
