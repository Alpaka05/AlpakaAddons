package net.alpaka.addons.features.prompts;

import net.minecraft.network.chat.ClickEvent;

import java.util.List;

/**
 * Something Hypixel asked in chat with a clickable answer: what the notice says, and what each
 * answer sends.
 *
 * @param title   the notice's heading, the NPC's name for a dialog
 * @param detail  the question or who is asking, or empty
 * @param choices the answers in the order their keys are given out, never empty
 */
public record ChatPrompt(Kind kind, String title, String detail, List<Choice> choices) {

    /** What raised the prompt; each kind has its own switch in the config. */
    public enum Kind {
        /** "Select an option: [Yes] [No]" from an NPC, or the older "Click an option: [YES] - [NO]". */
        DIALOG,
        /** An Abiphone call with its [PICK UP]. */
        ABIPHONE,
        TRADE,
        FRIEND,
        GUILD,
        DUEL,
        /** "Click HERE to re-queue into The Catacombs" after a run. */
        REQUEUE,
        /** "CLICK HERE to say gg!" under a rare drop announcement. */
        GG
    }

    /**
     * One answer: its label, and the click event Hypixel put on it in chat. The key sends what a
     * click on that piece of the chat line would send - the event's command, or for an NPC dialog
     * its click action - and nothing the mod made up.
     */
    public record Choice(String label, ClickEvent click) {
    }
}
