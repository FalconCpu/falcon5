`timescale 1ns/1ns

module cpu_read_fifo(
    input logic         clock,
    input logic         reset,

    // Connection to the cpu pipeline
    input logic         cpu_ready,      // CPU is ready to accept data
    output logic [4:0]  read_dest_reg,
    output logic [31:0] read_data,      // Data to be read       

    // Connection to the dcache
    input logic        cpu_dcache_rvalid,
    input logic [31:0] cpu_dcache_rdata,
    input logic [8:0]  cpu_dcache_rtag,

    // Connection to the divider
    input logic         div_valid,        // Divider has a valid result
    input logic [31:0] div_result,
    input logic [4:0]  div_dest_reg
);

logic [31:0]  data;

logic [4:0]      prev_read_dest_reg;
logic [31:0]     prev_read_data;
logic [4:0]      fifo1_reg,  prev_fifo1_reg;
logic [31:0]     fifo1_data, prev_fifo1_data;
logic [4:0]      fifo2_reg,  prev_fifo2_reg;
logic [31:0]     fifo2_data, prev_fifo2_data;
logic            error_fifo_overflow;
logic            prev_cpu_ready;

always_comb begin
    // defaults
    read_dest_reg = prev_read_dest_reg;
    read_data     = prev_read_data;
    fifo1_reg     = prev_fifo1_reg;
    fifo1_data    = prev_fifo1_data;
    fifo2_reg     = prev_fifo2_reg;
    fifo2_data    = prev_fifo2_data;
    error_fifo_overflow = 1'b0;

    case(cpu_dcache_rtag[8:5]) 
        4'b0000:   data = {{24{cpu_dcache_rdata[7]}}, cpu_dcache_rdata[7:0]};
        4'b0001:   data = {{24{cpu_dcache_rdata[15]}}, cpu_dcache_rdata[15:8]};
        4'b0010:   data = {{24{cpu_dcache_rdata[23]}}, cpu_dcache_rdata[23:16]};
        4'b0011:   data = {{24{cpu_dcache_rdata[31]}}, cpu_dcache_rdata[31:24]};
        4'b0100:   data = {{16{cpu_dcache_rdata[15]}}, cpu_dcache_rdata[15:0]};
        4'b0110:   data = {{16{cpu_dcache_rdata[31]}}, cpu_dcache_rdata[31:16]};
        4'b1000:   data = cpu_dcache_rdata;
        default:   data = 32'hx;
    endcase

    // If the cpu consumed the data then shift along
    if (prev_cpu_ready) begin
        read_dest_reg = fifo1_reg;
        read_data     = fifo1_data;
        fifo1_reg     = fifo2_reg;
        fifo1_data    = fifo2_data;
        fifo2_reg     = 5'b0;
        fifo2_data    = 32'hx;
    end

    // If new data has arrived then push it into the FIFO
    if (cpu_dcache_rvalid) begin
        if (read_dest_reg==5'b0) begin
            read_dest_reg = cpu_dcache_rtag[4:0];
            read_data     = data;
        end else if (fifo1_reg==5'b0) begin
            fifo1_reg     = cpu_dcache_rtag[4:0];
            fifo1_data    = data;
        end else if (fifo2_reg==5'b0) begin
            fifo2_reg     = cpu_dcache_rtag[4:0];
            fifo2_data    = data;
        end else
            error_fifo_overflow = 1'b1; // FIFO overflow
    end

    // If the divider has a valid result then push it into the FIFO
    if (div_valid) begin
        if (read_dest_reg==5'b0) begin
            read_dest_reg = div_dest_reg;
            read_data     = div_result;
        end else if (fifo1_reg==5'b0) begin
            fifo1_reg     = div_dest_reg;
            fifo1_data    = div_result;
        end else if (fifo2_reg==5'b0) begin
            fifo2_reg     = div_dest_reg;
            fifo2_data    = div_result;
        end else
            error_fifo_overflow = 1'b1; // FIFO overflow
    end



    if (reset) begin
        read_dest_reg = 5'b0;
        fifo1_reg     = 5'b0;
        fifo2_reg     = 5'b0;
    end
end

always_ff @(posedge clock) begin
    prev_read_data  <= read_data;
    prev_read_dest_reg <= read_dest_reg;
    prev_fifo1_data <= fifo1_data;
    prev_fifo1_reg  <= fifo1_reg;
    prev_fifo2_data <= fifo2_data;
    prev_fifo2_reg  <= fifo2_reg;
    prev_cpu_ready  <= cpu_ready;
    if (error_fifo_overflow) begin
        $display("ERROR %t: FIFO overflow in cpu_read_fifo", $time);
    end
end



endmodule