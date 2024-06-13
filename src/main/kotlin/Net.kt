package me.alex_s168

import blitz.collections.contents


class Net(inputIn: InNode, outputIn: OutNode) {
    var input = inputIn
        set(value) {
            killNode(field)
            field = value
            addNodeRec(field)
        }

    var output = outputIn
        set(value) {
            killNode(field)
            field = value
            addNodeRec(field)
        }

    fun killNode(node: Node) {
        allNodes.remove(node)
        node.wires.toList().forEach { it.kill() }
    }

    fun addNodeRec(node: Node) {
        if (node !in allNodes) {
            allNodes.add(node)
            allWires.addAll(node.wires)
            node.wires.forEach {
                addNodeRec(it.to)
                addNodeRec(it.from)
            }
        }
    }

    private var allNodes = mutableSetOf(input, output)
    private var allWires = mutableSetOf<Wire>()
        get() {
            field.removeAll { it.dead }
            return field
        }

    fun allNodesIter(): Iterable<Node> =
        allNodes

    fun allWiresIter(): Iterable<Wire> =
        allWires

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("nodes:\n")
        allNodes.forEach { node ->
            sb.append("  ")
            sb.append(node.toString())
            sb.appendLine()
        }
        return sb.toString()
    }

    fun expandOpt(from: Component?, platformSupportedComponents: List<Component>, newId: () -> Int) {
        while (allNodes.any { it is ComponentNode && it.component !in platformSupportedComponents }) {
            expandIter(from, platformSupportedComponents, newId)
        }
    }

    fun expandIter(from: Component?, platformSupportedComponents: List<Component>, newId: () -> Int) {
        val kill = mutableListOf<Node>()
        val add = mutableListOf<Node>()
        for (node in allNodes) {
            if (node is ComponentNode) {
                if (node.component !in platformSupportedComponents && (from ?: node) == node) {
                    kill.add(node)
                    when (node.component) {
                        Component.NOT -> {
                            val i = node.ports.inp(0)!!
                            val o = node.ports.out(1)

                            val nand = ComponentNode(newId(), Component.NAND)
                            nand.ports[0] = i.from()
                            nand.ports[1] = i.from()

                            o.forEach {
                                nand.ports[2] = WireEnd(it.to, it.toPort)
                            }

                            add.add(nand)
                        }

                        Component.AND -> {
                            val i0 = node.ports.inp(0)!!
                            val i1 = node.ports.inp(1)!!
                            val o = node.ports.out(2)

                            val nand = ComponentNode(newId(), Component.NAND)
                            nand.ports[0] = i0.from()
                            nand.ports[1] = i1.from()

                            add.add(nand)

                            val not = ComponentNode(newId(), Component.NOT)
                            not.ports[0] = WireEnd(nand, 2)

                            o.forEach {
                                not.ports[1] = WireEnd(it.to, it.toPort)
                            }

                            add.add(not)
                        }

                        Component.NAND -> {
                            val i0 = node.ports.inp(0)
                            val i1 = node.ports.inp(1)
                            val o = node.ports.out(2)

                            val and = ComponentNode(newId(), Component.AND)
                            and.ports[0] = i0!!.from()
                            and.ports[1] = i1!!.from()

                            add.add(and)

                            val not = ComponentNode(newId(), Component.NOT)
                            not.ports[0] = WireEnd(and, 2)

                            o.forEach {
                                not.ports[1] = WireEnd(it.to, it.toPort)
                            }

                            add.add(not)
                        }

                        Component.NOR -> {
                            val i0 = node.ports.inp(0)
                            val i1 = node.ports.inp(1)
                            val o = node.ports.out(2)

                            val or = ComponentNode(newId(), Component.OR)
                            or.ports[0] = i0!!.from()
                            or.ports[1] = i1!!.from()

                            add.add(or)

                            val not = ComponentNode(newId(), Component.NOT)
                            not.ports[0] = WireEnd(or, 2)

                            o.forEach {
                                not.ports[1] = WireEnd(it.to, it.toPort)
                            }

                            add.add(not)
                        }

                        Component.XOR -> {
                            val i0 = node.ports.inp(0)!!
                            val i1 = node.ports.inp(1)!!
                            val o = node.ports.out(2)

                            val nand = ComponentNode(newId(), Component.NAND)
                            nand.ports[0] = i0.from()
                            nand.ports[1] = i1.from()

                            add.add(nand)

                            val or = ComponentNode(newId(), Component.OR)
                            or.ports[0] = i0.from()
                            or.ports[1] = i1.from()

                            add.add(or)

                            val and = ComponentNode(newId(), Component.AND)
                            and.ports[0] = WireEnd(nand, 2)
                            and.ports[1] = WireEnd(or, 2)

                            o.forEach {
                                and.ports[2] = WireEnd(it.to, it.toPort)
                            }

                            add.add(and)
                        }

                        Component.OR -> {
                            val i0 = node.ports.inp(0)!!
                            val i1 = node.ports.inp(1)!!
                            val o = node.ports.out(2)

                            val not0 = ComponentNode(newId(), Component.NOT)
                            not0.ports[0] = i0.from()

                            add.add(not0)

                            val not1 = ComponentNode(newId(), Component.NOT)
                            not1.ports[0] = i1.from()

                            add.add(not1)

                            val nand = ComponentNode(newId(), Component.NAND)
                            nand.ports[0] = WireEnd(not0, 1)
                            nand.ports[1] = WireEnd(not1, 1)

                            o.forEach {
                                nand.ports[2] = WireEnd(it.to, it.toPort)
                            }

                            add.add(nand)
                        }

                        Component.HALF_ADD -> {
                            val i0 = node.ports.inp(0)!!
                            val i1 = node.ports.inp(1)!!
                            val sum = node.ports.out(2)
                            val carr = node.ports.out(3)

                            val xor = ComponentNode(newId(), Component.XOR)
                            xor.ports[0] = i0.from()
                            xor.ports[1] = i1.from()

                            sum.forEach {
                                xor.ports[2] = WireEnd(it.to, it.toPort)
                            }

                            add.add(xor)

                            val and = ComponentNode(newId(), Component.AND)
                            and.ports[0] = i0.from()
                            and.ports[1] = i1.from()

                            carr.forEach {
                                and.ports[2] = WireEnd(it.to, it.toPort)
                            }

                            add.add(and)
                        }

                        Component.FULL_ADD -> {
                            val i0 = node.ports.inp(0)!!
                            val i1 = node.ports.inp(1)!!
                            val i2 = node.ports.inp(2)!!
                            val sum = node.ports.out(3)
                            val carr = node.ports.out(4)

                            val ha0 = ComponentNode(newId(), Component.HALF_ADD)
                            ha0.ports[0] = i0.from()
                            ha0.ports[1] = i1.from()

                            add.add(ha0)

                            val ha1 = ComponentNode(newId(), Component.HALF_ADD)
                            ha1.ports[0] = i2.from()
                            ha1.ports[1] = WireEnd(ha0, 2)

                            sum.forEach {
                                ha1.ports[2] = WireEnd(it.to, it.toPort)
                            }

                            add.add(ha1)

                            val or = ComponentNode(newId(), Component.OR)
                            or.ports[0] = WireEnd(ha0, 3)
                            or.ports[1] = WireEnd(ha1, 3)

                            carr.forEach {
                                or.ports[2] = WireEnd(it.to, it.toPort)
                            }

                            add.add(or)
                        }

                        else -> error("don't know how to expand ${node.component} to $platformSupportedComponents")
                    }
                    break
                }
            }
        }
        kill.forEach {
            killNode(it)
        }
        add.forEach {
            addNodeRec(it)
        }
    }

    fun check() {
        allNodes.forEach { node ->
            when (node) {
                is ComponentNode -> {
                    require(node.inputs().size == node.component.inPortCount) {
                        "failed on $node"
                    }
                }
            }
        }
    }

    fun computeLayers(): List<List<Node>> {
        val layers = mutableListOf<List<Node>>()
        val remNodes = allNodes.toMutableList()

        while (remNodes.isNotEmpty()) {
            val layer = mutableListOf<Node>()

            remNodes.toList().forEach { node ->
                val inputs = node.inputs()

                if (inputs.all { i -> layers.any { i.node in it } }) {
                    remNodes.remove(node)
                    layer.add(node)
                }
            }

            if (layer.isEmpty() && remNodes.isNotEmpty()) {
                error("recursive dependency detected in ${remNodes.contents}")
            }

            layers.add(layer)
        }

        return layers
    }
}