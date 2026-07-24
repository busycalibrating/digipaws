package neth.iecal.curbox.data.models

data class KeywordBlocker(
    val isActive: Boolean = false,
    val keywordGroups: List<KeywordGroup> = emptyList(),
    val blockAllExceptSupported: Boolean = false
)

data class KeywordGroup(
    val id: String = "",
    val name: String = "name",
    val selectedKeywords: List<String> = listOf(),
    val blockingType: AppBlockingType = AppBlockingType.Usage,
    val isActive: Boolean = false,
    val temporarilyDisabledUntilMs: Long = 0L,
    val setting: String = "",
    /** Optional timed group that defines when this usage allowance is available. */
    val linkedTimeGroupId: String? = null,
    val warningScreenConfig: AppBlockerWarningScreenConfig = AppBlockerWarningScreenConfig()
)
