package me.alex_s168

import sun.misc.Unsafe

object GPU {
    init {
        System.loadLibrary("host")
        nStart()
    }

    data class Device(
        internal val platform: Long,
        internal val devid: Long,
    ) {
        val computeCores = nCompueCores(platform, devid)

        override fun toString() =
            "$platform:$devid ($computeCores cores)"
    }

    val devices = sequence {
        for (plat in 0 ..< nNumPlatforms()) {
            for (dev in 0 ..< nNumDevices(plat)) {
                yield(Device(plat, dev))
            }
        }
    }

    class Circuit(
        val id: Long,
        private val ninputs: Long,
        private val noutputs: Long,
        private val nbytes: Long,
    ): AutoCloseable {
        fun run() {
            nCircuitRun(id)
        }

        override fun close() {
            nCircuitFree(id)
            UNSAFE.freeMemory(ninputs)
            UNSAFE.freeMemory(noutputs)
            UNSAFE.freeMemory(nbytes)
        }

        override fun hashCode() =
            id.hashCode()

        override fun equals(other: Any?) =
            other is Circuit && other.id == id

        override fun toString() = "Circuit($id)"
    }

    data class CircuitConfig(
        val globalWiresCount: Long,
        val outputsCount: Long,
        val numLocalWires: Long,
        val numLayers: Long,
        val numLayerNodes: Long,  // num compute cores
    )

    fun newCircuit(
        device: Device,
        cfg: CircuitConfig,
        /** copied */
        nodes: ByteArray,
        /** copied */
        inputs: ByteArray
    ): Circuit {
        val nnodes = nodes.copyToDirect()
        val ninputs = inputs.copyToDirect()
        val noutputs = UNSAFE.allocateMemory(cfg.outputsCount)

        val numNodeBytes = nodes.size / cfg.numLayerNodes / cfg.numLayers
        println(numNodeBytes)

        val id = nCircuitCompile(
            device.platform,
            device.devid,
            cfg.globalWiresCount,
            inputs.size.toLong(),
            cfg.outputsCount,
            cfg.numLocalWires,
            nnodes,
            numNodeBytes,
            cfg.numLayers,
            cfg.numLayerNodes,
            ninputs,
            noutputs)

        if (id == 0L)
            error("failed to create circuit")

        return Circuit(id, ninputs, noutputs, nnodes)
    }

    // 0 = ok
    private external fun nStart(): Long

    private external fun nComuteCoresPerUnit(platform: Long, device: Long): Long
    private external fun nCompueCores(platform: Long, device: Long): Long

    // 0 = error
    private external fun nCircuitCompile(
        platform: Long,
        device: Long,
        globalWiresCount: Long,
        inputsCount: Long,
        outputsCount: Long,
        numLocalWires: Long,
        ptrNodeConfigBytes: Long,
        numNodeBytes: Long,
        numLayers: Long,
        numLayerNodes: Long,  // num compute cores
        ptrInputs: Long,
        ptrOutputs: Long
    ): Long

    private external fun nCircuitRun(circuit: Long)

    private external fun nCircuitFree(circuit: Long)

    private external fun nEnd(): Long

    private external fun nNumPlatforms(): Long

    private external fun nNumDevices(platform: Long): Long

    private val UNSAFE = Unsafe::class.java
        .getDeclaredField("theUnsafe")
        .also { it.isAccessible = true }
        .get(null) as Unsafe

    private fun ByteArray.copyToDirect(): Long {
        val new = UNSAFE.allocateMemory(size.toLong())
        forEachIndexed { index, byte ->
            UNSAFE.setMemory(new + index, 1, byte)
        }
        return new
    }
}