package neth.iecal.curbox.data.models

import java.util.UUID

/**
 * Stands in for the end of a session that has no timer and runs until it is turned off.
 * Far enough ahead that every "has it finished yet" check reads as still running.
 */
const val FOCUS_NO_END_TIME = Long.MAX_VALUE

data class ManualFocusGroup(
    val groupId: String = UUID.randomUUID().toString(),
    val groupName: String = "",
    val packages: HashSet<String> = hashSetOf(),
    val keywords: HashSet<String> = hashSetOf(),
    val blockMode: FocusBlockMode = FocusBlockMode.BLOCK_SELECTED,
    val exitable: Boolean = true,
    /**
     * Runs until it is turned off again rather than for a set time. Suits a group driven
     * by an NFC tag, where the same tap starts and ends the session.
     */
    val isUntimed: Boolean = false,
    val autoTurnOnDnd: Boolean = false
){
    override fun toString(): String {
        val mode = if(blockMode == FocusBlockMode.BLOCK_SELECTED) "included" else "excluded"
        return if (keywords.isNotEmpty()) {
            "$groupName (${packages.size} apps, ${keywords.size} websites $mode)"
        } else {
            "$groupName (${packages.size} $mode apps)"
        }
    }

}
