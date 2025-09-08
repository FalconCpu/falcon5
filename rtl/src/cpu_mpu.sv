`timescale 1ns/1ns

module cpu_mpu (
    input logic         clock,
    input logic         reset,

    // Inputs from the CPU core
    input  logic        p3_mem_request,  // High if this is a memory access
    input  logic        p3_mem_write,    // Read (0) or write (1)
    input  logic [31:0] p3_mem_addr,     // Address to DCache
    input  logic        supervisor_mode, // CPU is in supervisor mode
    
    // Inputs from CFG registers
    input  logic        p3_mpu_reset,    // Set to reset the MPU (clear all regions)
    input  logic        p3_mpu_add,      // Set to add a new MPU region
    input  logic [31:0] p3_mpu_data,     // Data for new MPU region

    output logic        p4_load_fault,   // High if the current memory access is not allowed
    output logic        p4_store_fault  
);

// regions are described as
// [31:12]  base address
// [10]     execute enable  // (not implemented yet)
// [5]      write enable
// [4]      read enable
// [3:0]    size: 0=4k, 1=8k, 2=16k, ... 15=32M

logic [19:0] region_address[0:7];
logic [19:0] region_mask[0:7];
logic [7:0]   region_write;
logic [7:0]   region_read;
logic [2:0]   region_count;

integer i;
logic p3_load_fault, p3_store_fault;

always_comb begin
    p3_load_fault  = p3_mem_request & ~p3_mem_write;    // Assume fault unless proven otherwise
    p3_store_fault = p3_mem_request &  p3_mem_write;
    i = 0;

    // If in supervisor mode, all accesses are allowed
    if (supervisor_mode || p3_mem_request==1'b0) begin
        p3_load_fault  = 1'b0;
        p3_store_fault = 1'b0;
    end else begin
        // Check each region to see if the address is allowed
        for(i=0; i<8; i=i+1) begin
            if ( ((p3_mem_addr[31:12] ^ region_address[i]) & region_mask[i]) == 20'h0) begin
                if (region_write[i])
                    p3_store_fault = 1'b0; // Write allowed
                if (region_read[i]) 
                    p3_load_fault  = 1'b0; // Read allowed
            end
        end
    end
end

// sequential region management
always_ff @(posedge clock) begin
    p4_load_fault  <= p3_load_fault;
    p4_store_fault <= p3_store_fault;

    if (reset || p3_mpu_reset) begin
        // Clear all regions
        region_count <= 3'b0;
        for(i=0; i<8; i=i+1) begin
            region_address[i] <= 20'bx;
            region_mask[i]    <= 20'bx;
            region_write[i]   <= 1'b0;
            region_read[i]    <= 1'b0;
        end
    end else if (p3_mpu_add && p3_mpu_data[5:4]!=2'b0) begin
        // Add a new region - overwrite the oldest if full
        region_address[region_count] <= p3_mpu_data[31:12];
        region_mask[region_count]    <= ~( (1 << (p3_mpu_data[3:0])) - 1'b1);
        region_write[region_count]   <= p3_mpu_data[5];
        region_read[region_count]    <= p3_mpu_data[4];
        region_count <= region_count + 1'b1;
        end
    end

wire unused = &{1'b0, p3_mem_addr[11:0], p3_mpu_data[11:6]}; // avoid warnings

endmodule