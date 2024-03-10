package net.cosmicreachfabric.cosmicreach.provider.patch;

import net.cosmicreachfabric.cosmicreach.provider.services.CosmicReachHooks;
import net.fabricmc.loader.impl.game.patch.GamePatch;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ListIterator;
import java.util.function.Consumer;
import java.util.function.Function;

public class CosmicReachEntrypointPatch extends GamePatch {

    @Override
    public void process(FabricLauncher launcher, Function<String, ClassNode> classSource, Consumer<ClassNode> classEmitter) {
        String entrypoint = launcher.getEntrypoint();
        if (!entrypoint.startsWith("finalforeach.")) {
            return;
        }
        ClassNode mainClass = classSource.apply("finalforeach.cosmicreach.BlockGame");

        MethodNode initMethod = findMethod(mainClass, (method) -> method.name.equals("render") && method.desc.equals("()V"));

        if (initMethod == null) {
            throw new RuntimeException("Could not find init method in " + entrypoint + "!");
        }
        Log.debug(LogCategory.GAME_PATCH, "Found init method: %s -> %s", entrypoint, mainClass.name);

        Log.debug(LogCategory.GAME_PATCH, "Patching init method %s%s", initMethod.name, initMethod.desc);
        ListIterator<AbstractInsnNode> it = initMethod.instructions.iterator();
        it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CosmicReachHooks.INTERNAL_NAME, "init", "()V", false));

        classEmitter.accept(mainClass);
    }
}