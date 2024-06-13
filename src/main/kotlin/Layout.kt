package me.alex_s168

import blitz.collections.Matrix

fun Matrix<CoreExecConfig>.layout(): Layout {
    val tra = transposeCopy()

    val allVarsPerCore = tra.rows.map { it.flatMap { it.exec.map { it.id } } }
    val allVarUsages = allVarUsages()

    val global = mutableSetOf<Int>()
    val perCore = allVarsPerCore.mapIndexed { col, vars ->
        val local = mutableSetOf<Int>()
        vars.forEach { v ->
            val usages = allVarUsages
                .filter { it.first == v }
            if (usages.any { it.second.first != col })
                global.add(v)
            else
                local.add(v)
        }
        CoreConfig(local)
    }

    return Layout(
        perCore,
        this,
        global
    )
}


data class CoreConfig(
    val localNodes: MutableSet<Int>
) {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Core:")
        localNodes.forEach { v ->
            sb.appendLine("local $v")
        }
        return sb.toString()
    }
}

data class Layout(
    val configs: List<CoreConfig>,
    val layers: Matrix<CoreExecConfig>,
    val globals: MutableSet<Int>,
) {
    init {
        require(configs.size == layers.width)
    }

    override fun toString(): String {
        val mat = Matrix(layers.width, layers.height + 1) { x, y ->
            if (y == 0) configs[x].toString()
            else layers[x, y - 1].toString()
        }

        val sb = StringBuilder()
        sb.appendLine("LAYOUT")
        sb.appendLine("globals: ${globals.joinToString()}\n")
        sb.append(mat)

        return sb.toString()
    }
}

data class CoreExecConfig(
    val exec: MutableList<Node>
) {
    override fun toString() =
        exec.joinToString("\n")
}