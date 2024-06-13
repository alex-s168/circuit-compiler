#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <CL/CL.h>
#include "me_alex_s168_GPU.h"

// to make lsp happy 
#ifndef JNIEXPORT
# define JNIEXPORT /**/ 
# define JNIEnv void 
# define jobject int 
# define JNICALL /**/
# define jlong uint64_t
# error NO JNI 
#endif

// lot of code stolen from https://github.com/rsnemmen/OpenCL-examples/blob/master/add_numbers

typedef struct {
    size_t  global_wires_count;
    size_t  inputs_count;
    size_t  outputs_count;
    size_t  num_local_wires;
    uint8_t *node_config_bytes;
    size_t  num_node_bytes;
    size_t  num_layers;
    size_t  num_layer_nodes;
} Circuit;

cl_program build_program(cl_context ctx, cl_device_id dev, const char* filename, Circuit *circ);

typedef struct {
    Circuit  circ;
    uint8_t *inp;
    uint8_t *out;
    cl_context context;
    cl_program program;
    cl_mem buf_nodes;
    cl_mem buf_wires;
    cl_mem buf_inputs;
    cl_mem buf_outputs;
    cl_kernel kernel;
    cl_command_queue queue;
} RtCircuit;

cl_uint numPlatforms = 0;
cl_device_id** platformDevices = NULL;

JNIEXPORT jlong JNICALL Java_me_alex_1s168_GPU_nStart(JNIEnv *jnienv, jobject this) {
    if (platformDevices != NULL) {
        fprintf(stderr, "lib already initialized");
        return 1;
    }

    cl_int CL_err = 0;

    CL_err = clGetPlatformIDs(0, NULL, &numPlatforms);
    if (CL_err != CL_SUCCESS) {
        fprintf(stderr, "clGetPlatformIDs(%i)\n", CL_err);
        return 2;
    }

    cl_platform_id* platforms = malloc(sizeof(cl_platform_id) * numPlatforms);
    CL_err = clGetPlatformIDs(numPlatforms, platforms, NULL);
    if (CL_err != CL_SUCCESS) {
        fprintf(stderr, "clGetPlatformIDs(%i)\n", CL_err);
        return 3;
    }

    platformDevices = malloc(sizeof(cl_device_id*) * numPlatforms);
    for (int i = 0; i < numPlatforms; i++) {
        // get all devices
        cl_uint deviceCount;
        clGetDeviceIDs(platforms[i], CL_DEVICE_TYPE_ALL, 0, NULL, &deviceCount);
        cl_device_id* devices = (cl_device_id*) malloc(sizeof(cl_device_id) * (deviceCount + 1));
        clGetDeviceIDs(platforms[i], CL_DEVICE_TYPE_ALL, deviceCount, devices, NULL);
        devices[deviceCount] = NULL;
        platformDevices[i] = devices;
    }
    free(platforms);

    return 0;
}


JNIEXPORT jlong JNICALL Java_me_alex_1s168_GPU_nEnd(JNIEnv *env, jobject this) {
    if (platformDevices == NULL)
        return 1;
    for (size_t i = 0; i < numPlatforms; i ++)
        free(platformDevices[i]);
    free(platformDevices);
    platformDevices = NULL;
    numPlatforms = 0;
    return 0;
}

JNIEXPORT jlong JNICALL Java_me_alex_1s168_GPU_nNumPlatforms(JNIEnv *env, jobject this) {
    return numPlatforms;
}

JNIEXPORT jlong JNICALL Java_me_alex_1s168_GPU_nNumDevices(JNIEnv *env, jobject this, jlong platform) {
    cl_device_id* dev = platformDevices[platform];
    size_t size = 0;
    for (; dev[size]; size ++);
    return size; 
}

JNIEXPORT jlong JNICALL Java_me_alex_1s168_GPU_nCompueCores(JNIEnv *env, jobject this, jlong plat, jlong dev) {
    cl_device_id device = platformDevices[plat][dev];

    cl_uint maxCu;
    clGetDeviceInfo(device, CL_DEVICE_MAX_COMPUTE_UNITS,
                    sizeof(maxCu), &maxCu, NULL);

    return maxCu;
}

JNIEXPORT jlong JNICALL Java_me_alex_1s168_GPU_nComuteCoresPerUnit(JNIEnv *env, jobject this, jlong plat, jlong dev) {
    cl_device_id device = platformDevices[plat][dev];

    cl_uint maxWg;
    clGetDeviceInfo(device, CL_DEVICE_MAX_WORK_GROUP_SIZE,
                    sizeof(maxWg), &maxWg, NULL);

    cl_uint maxCu = Java_me_alex_1s168_GPU_nCompueCores(env, this, plat, dev);

    return maxCu / maxWg;
}

JNIEXPORT jlong JNICALL Java_me_alex_1s168_GPU_nCircuitCompile(JNIEnv *env, jobject this,
                                   jlong plat,
                                   jlong dev,
                                   jlong globalWiresCount,
                                   jlong inputsCount,
                                   jlong outputsCount,
                                   jlong numLocalWires,
                                   jlong ptrNodeConfigBytes,
                                   jlong numNodeBytes,
                                   jlong numLayers,
                                   jlong numLayerNodes, // num compute cores
                                   jlong ptrInputs,
                                   jlong ptrOutputs)  {
    RtCircuit *circ = malloc(sizeof(RtCircuit));
    circ->circ.num_layers = numLayers;
    circ->circ.num_layer_nodes = numLayerNodes;
    circ->circ.num_node_bytes = numNodeBytes;
    circ->circ.num_local_wires = numLocalWires;
    circ->circ.node_config_bytes = (uint8_t*) ptrNodeConfigBytes;
    circ->circ.global_wires_count = globalWiresCount;
    circ->circ.inputs_count = inputsCount;
    circ->circ.outputs_count = outputsCount;
    circ->inp = (uint8_t*) ptrInputs;
    circ->out = (uint8_t*) ptrOutputs;

    cl_int CL_err = 0;

    cl_device_id device = platformDevices[plat][dev];

    circ->context = clCreateContext(NULL, 1, &device, NULL, NULL, &CL_err);
    if (CL_err != CL_SUCCESS) {
        fprintf(stderr, "could not create cl context (%d)\n", CL_err);
        return 1;
    }

    circ->program = build_program(circ->context, device, "core.c", &circ->circ);

    size_t node_config_bytes_size = circ->circ.num_layers * circ->circ.num_layer_nodes * circ->circ.num_node_bytes;

    size_t global_size = circ->circ.num_layer_nodes * 1;
    cl_int num_groups = 1;
    circ->buf_nodes = clCreateBuffer(circ->context, CL_MEM_READ_WRITE |
            CL_MEM_COPY_HOST_PTR, node_config_bytes_size, circ->circ.node_config_bytes, &CL_err);
    circ->buf_wires = clCreateBuffer(circ->context, CL_MEM_READ_WRITE |
            0, circ->circ.global_wires_count, NULL, &CL_err);
    circ->buf_inputs = clCreateBuffer(circ->context, CL_MEM_READ_ONLY |
            CL_MEM_COPY_HOST_PTR, circ->circ.inputs_count, circ->inp, &CL_err);
    circ->buf_outputs = clCreateBuffer(circ->context, CL_MEM_READ_WRITE |
            CL_MEM_COPY_HOST_PTR, circ->circ.outputs_count, circ->out, &CL_err);
    if (CL_err != CL_SUCCESS) {
        fprintf(stderr, "error code %i\n", CL_err);
        return 1;  
    }
    
    circ->queue = clCreateCommandQueue(circ->context, device, 0, &CL_err);
    if (CL_err != CL_SUCCESS) {
        fprintf(stderr, "Couldn't create a command queue\n");
        return 1;  
    }

    circ->kernel = clCreateKernel(circ->program, "compute", &CL_err);
    if (CL_err != CL_SUCCESS) {
        fprintf(stderr, "Couldn't create a kernel\n");
        return 1;
    }

    CL_err = clSetKernelArg(circ->kernel, 0, sizeof(cl_mem), &circ->buf_wires); 
    CL_err |= clSetKernelArg(circ->kernel, 1, sizeof(cl_mem), &circ->buf_inputs);
    CL_err |= clSetKernelArg(circ->kernel, 2, sizeof(cl_mem), &circ->buf_outputs);
    CL_err |= clSetKernelArg(circ->kernel, 3, sizeof(cl_mem), &circ->buf_nodes);
    CL_err |= clSetKernelArg(circ->kernel, 4, circ->circ.num_local_wires * circ->circ.num_layer_nodes, NULL);
    if (CL_err != CL_SUCCESS) {
        fprintf(stderr, "Couldn't create a kernel argument\n");
        return 1;
    }

    return (uint64_t) circ;
}

JNIEXPORT void JNICALL Java_me_alex_1s168_GPU_nCircuitRun(JNIEnv *env, jobject this, jlong circuitId) {
    RtCircuit *circ = (void*)circuitId;
    cl_int CL_err = 0;

    size_t local_size = circ->circ.num_layer_nodes;
    size_t global_size = local_size * 1;

    CL_err = clEnqueueNDRangeKernel(circ->queue, circ->kernel, 1, NULL, &global_size, &local_size, 0, NULL, NULL); 
    if (CL_err != CL_SUCCESS) {
        fprintf(stderr, "Couldn't enqueue the kernel\n");
        return;
    }

    CL_err = clEnqueueReadBuffer(circ->queue, circ->buf_outputs, CL_TRUE, 0, circ->circ.outputs_count, circ->out, 0, NULL, NULL);
    if (CL_err != CL_SUCCESS) {
        fprintf(stderr, "Couldn't read the buffer\n");
        return;
    }
}

JNIEXPORT void JNICALL Java_me_alex_1s168_GPU_nCircuitFree(JNIEnv *env, jobject this, jlong circuitId) {
    RtCircuit *circ = (void*)circuitId;

    clReleaseKernel(circ->kernel);
    clReleaseMemObject(circ->buf_wires);
    clReleaseMemObject(circ->buf_inputs);
    clReleaseMemObject(circ->buf_outputs);
    clReleaseMemObject(circ->buf_nodes);
    clReleaseCommandQueue(circ->queue);
    clReleaseProgram(circ->program);
    clReleaseContext(circ->context);
    
    free(circ);
}

/* Create program from a file and compile it */
cl_program build_program(cl_context ctx, cl_device_id dev, const char* filename, Circuit *circ) {
   static char defines[512];
   sprintf(defines, "-DNUM_LOCAL_WIRES=%zu -DNUM_NODE_BYTES=%zu -DNUM_LAYERS=%zu -DNUM_LAYER_NODES=%zu", circ->num_local_wires, circ->num_node_bytes, circ->num_layers, circ->num_layer_nodes);

   cl_program program;
   FILE *program_handle;
   char *program_buffer, *program_log;
   size_t program_size, log_size;
   int err;

   /* Read program file and place content into buffer */
   program_handle = fopen(filename, "r");
   if(program_handle == NULL) {
      perror("Couldn't find the program file");
      exit(1);
   }
   fseek(program_handle, 0, SEEK_END);
   program_size = ftell(program_handle);
   rewind(program_handle);
   program_buffer = (char*)malloc(program_size + 1);
   program_buffer[program_size] = '\0';
   fread(program_buffer, sizeof(char), program_size, program_handle);
   fclose(program_handle);

   /* Create program from file 

   Creates a program from the source code in the add_numbers.cl file. 
   Specifically, the code reads the file's content into a char array 
   called program_buffer, and then calls clCreateProgramWithSource.
   */
   program = clCreateProgramWithSource(ctx, 1, 
      (const char**)&program_buffer, &program_size, &err);
   if(err < 0) {
      perror("Couldn't create the program");
      exit(1);
   }
   free(program_buffer);

   /* Build program 

   The fourth parameter accepts options that configure the compilation. 
   These are similar to the flags used by gcc. For example, you can 
   define a macro with the option -DMACRO=VALUE and turn off optimization 
   with -cl-opt-disable.
   */
   err = clBuildProgram(program, 0, NULL, defines, NULL, NULL);
   if(err < 0) {

      /* Find size of log and print to std output */
      clGetProgramBuildInfo(program, dev, CL_PROGRAM_BUILD_LOG, 
            0, NULL, &log_size);
      program_log = (char*) malloc(log_size + 1);
      program_log[log_size] = '\0';
      clGetProgramBuildInfo(program, dev, CL_PROGRAM_BUILD_LOG, 
            log_size + 1, program_log, NULL);
      printf("%s\n", program_log);
      free(program_log);
      exit(1);
   }

   return program;
}
