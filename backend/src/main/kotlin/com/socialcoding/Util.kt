package com.socialcoding

val String.camelCase
    get() =
        split("_")
            .mapIndexed { i, s ->
                if (i == 0) s else s.replaceFirstChar { it.uppercase() }
            }
            .joinToString("")
