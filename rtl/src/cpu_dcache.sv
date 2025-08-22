`timescale 1ns/1ns

module cpu_dcache(
    input logic        clock,
    input logic        reset,

    input  logic        cpu_dcache_request,
    output logic        cpu_dcache_ready,
    input  logic        cpu_dcache_write,
    input  logic [31:0] cpu_dcache_address,
    input  logic        cpu_dcache_burst,
    input  logic [3:0]  cpu_dcache_wstrb,
    input  logic [31:0] cpu_dcache_wdata,

    output logic        cpu_dcache_rvalid,
    output logic [31:0] cpu_dcache_rdata,
    output logic [8:0]  cpu_dcache_rtag
);

// Temporarily just instance a block of memory here in place of the dcache
logic [7:0] ram0[0:16383];
logic [7:0] ram1[0:16383];
logic [7:0] ram2[0:16383];
logic [7:0] ram3[0:16383];

assign cpu_dcache_ready = 1'b1;

always_ff @(posedge clock ) begin
    if (cpu_dcache_request && cpu_dcache_write) begin
        // Write data to the cache
        if (cpu_dcache_wstrb[0]) ram0[cpu_dcache_address[15:2]] <= cpu_dcache_wdata[7:0];
        if (cpu_dcache_wstrb[1]) ram1[cpu_dcache_address[15:2]] <= cpu_dcache_wdata[15:8];
        if (cpu_dcache_wstrb[2]) ram2[cpu_dcache_address[15:2]] <= cpu_dcache_wdata[23:16];
        if (cpu_dcache_wstrb[3]) ram3[cpu_dcache_address[15:2]] <= cpu_dcache_wdata[31:24];
        $display("[%8x]=%8x %x", cpu_dcache_address, cpu_dcache_wdata, cpu_dcache_wstrb);
    end

    cpu_dcache_rdata <= {ram3[cpu_dcache_address[15:2]],
                         ram2[cpu_dcache_address[15:2]],
                         ram1[cpu_dcache_address[15:2]],
                         ram0[cpu_dcache_address[15:2]]};
    cpu_dcache_rtag   <= cpu_dcache_wdata[8:0];
    cpu_dcache_rvalid <= cpu_dcache_request && !cpu_dcache_write;
end



endmodule