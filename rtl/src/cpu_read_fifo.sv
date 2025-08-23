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
    input logic        div_valid,        // Divider has a valid result
    input logic [31:0] div_result,
    input logic [4:0]  div_dest_reg,

    // Connection to the address decoder
    input  logic        cpu_dec_rvalid,
    input  logic [31:0] cpu_dec_rdata,
    input  logic [8:0]  cpu_dec_rtag
);

localparam FIFO_DEPTH = 4;

logic [31:0]  data;     // Data from the dcache 
logic [31:0]  dec_data; // Data from the address decoder

logic [31:0]     fifo_data[0:3], next_fifo_data[0:FIFO_DEPTH-1];
logic [4:0]      fifo_reg[0:3],  next_fifo_reg[0:FIFO_DEPTH-1];
logic [FIFO_DEPTH-1:0]  fifo_valid,next_fifo_valid;

logic            error_fifo_overflow;
integer          i; 

assign read_dest_reg = fifo_reg[0];
assign read_data     = fifo_data[0];

task automatic push_fifo(input logic [31:0] val, input logic [4:0] regid);
    if (!next_fifo_valid[0]) begin
        next_fifo_data[0] = val; 
        next_fifo_reg[0] = regid;
        next_fifo_valid[0] = 1'b1;
    end else if (!next_fifo_valid[1]) begin
        next_fifo_data[1] = val; 
        next_fifo_reg[1] = regid;
        next_fifo_valid[1] = 1'b1;
    end else if (!next_fifo_valid[2]) begin
        next_fifo_data[2] = val; 
        next_fifo_reg[2] = regid;
        next_fifo_valid[2] = 1'b1;
    end else if (!next_fifo_valid[3]) begin
        next_fifo_data[3] = val; 
        next_fifo_reg[3] = regid;
        next_fifo_valid[3] = 1'b1;
    end else begin
        error_fifo_overflow = 1'b1;
    end
endtask


always_comb begin
    // defaults
    for(i=0; i<FIFO_DEPTH; i=i+1) begin
        next_fifo_data[i]  = fifo_data[i];
        next_fifo_reg[i]   = fifo_reg[i];
        next_fifo_valid[i] = fifo_valid[i];
    end
    error_fifo_overflow = 1'b0;

    // If the cpu consumed the data last cycle then shift along
    if (cpu_ready) begin
        for(i=0; i<FIFO_DEPTH-1; i=i+1) begin
            next_fifo_data[i]  = fifo_data[i+1];
            next_fifo_reg[i]   = fifo_reg[i+1];
            next_fifo_valid[i] = fifo_valid[i+1];
        end
        next_fifo_data[FIFO_DEPTH-1]  = 32'hx;
        next_fifo_reg[FIFO_DEPTH-1]   = 5'b0;
        next_fifo_valid[FIFO_DEPTH-1] = 1'b0;
    end


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
    if (cpu_dcache_rvalid) push_fifo(data, cpu_dcache_rtag[4:0]);

    case(cpu_dec_rtag[8:5]) 
        4'b0000:   dec_data = {{24{cpu_dec_rdata[7]}}, cpu_dec_rdata[7:0]};
        4'b0001:   dec_data = {{24{cpu_dec_rdata[15]}}, cpu_dec_rdata[15:8]};
        4'b0010:   dec_data = {{24{cpu_dec_rdata[23]}}, cpu_dec_rdata[23:16]};
        4'b0011:   dec_data = {{24{cpu_dec_rdata[31]}}, cpu_dec_rdata[31:24]};
        4'b0100:   dec_data = {{16{cpu_dec_rdata[15]}}, cpu_dec_rdata[15:0]};
        4'b0110:   dec_data = {{16{cpu_dec_rdata[31]}}, cpu_dec_rdata[31:16]};
        4'b1000:   dec_data = cpu_dec_rdata;
        default:   dec_data = 32'hx;
    endcase
    if (cpu_dec_rvalid) push_fifo(dec_data, cpu_dec_rtag[4:0]);

    if (div_valid) push_fifo(div_result, div_dest_reg);


    if (reset) begin
        for(i=0; i<FIFO_DEPTH; i=i+1) begin
            next_fifo_data[i]  = 32'hx;
            next_fifo_reg[i]   = 5'b0;
            next_fifo_valid[i] = 1'b0;
        end
    end
end

always_ff @(posedge clock) begin
    for (i=0; i<4; i=i+1) begin
        fifo_data[i]  <= next_fifo_data[i];
        fifo_reg[i]   <= next_fifo_reg[i];
        fifo_valid[i] <= next_fifo_valid[i];
    end

    if (error_fifo_overflow) begin
        $display("ERROR %t: FIFO overflow in cpu_read_fifo", $time);
    end
end



endmodule