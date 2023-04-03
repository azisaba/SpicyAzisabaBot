package net.azisaba.spicyazisababot.permission

enum class GlobalPermissionNode(val node: String) {
    EditGlobalPermissions("edit-global-permissions"),
    ChatGPTModelAll("chatgpt.model.all"),
    ChatGPTModelGPT35("chatgpt.model.gpt-3.5-turbo"),
    ChatGPTModelGPT4("chatgpt.model.gpt-4"),
    ChatGPTModelGPT4Context32k("chatgpt.model.gpt-4-32k"),
    ;

    companion object {
        val nodeMap = values().associateBy { it.node }
    }

    override fun toString(): String = "$name[$node]"
}
