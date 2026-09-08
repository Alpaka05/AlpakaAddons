package net.alpaka.addons.mixin;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the pitch a sound instance was created with.
 *
 * {@code SoundInstance.getPitch()} multiplies that by the resolved sound's own pitch, and the
 * resolution only happens inside {@code SoundEngine.play} - so at the head of that method, where the
 * mod decides about replacements, calling it dereferences a null and crashes. The raw field is what
 * the server asked for, which is also the value the Etherwarp cue is recognised by.
 */
@Mixin(AbstractSoundInstance.class)
public interface AbstractSoundInstanceAccessor {

    @Accessor("pitch")
    float alpaka$rawPitch();
}
