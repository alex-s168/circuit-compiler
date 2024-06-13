package me.alex_s168

import blitz.collections.Matrix
import blitz.logic.then

fun List<List<Node>>.optimizeLayers(maxUnitCompute: Int): Matrix<CoreExecConfig> {
    val mat = Matrix(maxOf { it.size }, size) { _, _ -> CoreExecConfig(mutableListOf()) }

    forEachIndexed { row, nodes ->
        nodes.forEach { node ->
            val deps = node.inputs()
            val placed = mat
                .elementsWithIndexes()
                .find {
                        (node) -> deps.any { dep ->
                    node.exec.any {
                        it.wires.any { it.from() == dep || it.to() == dep }
                    }
                }
                }
                ?.let { (_, loc) ->
                    (mat[loc.first, row].exec.size < maxUnitCompute).then {
                        mat[loc.first, row].exec.add(node)
                    }
                }
                ?: false
            if (!placed) {
                val col = mat.rows[row].indexOfFirst { it.exec.size < maxUnitCompute }
                mat[col, row].exec.add(node)
            }
        }
    }

    return mat
}