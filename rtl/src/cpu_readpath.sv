`timescale 1ns/1ns

module cpu_readpath(
    input logic         clock,
    input logic         reset,

    // Inputs from DCACHE
    input logic         cpu_dcache_rvalid,     // Read data is availible
    input logic [31:0]  cpu_dcache_rdata,      // The read data.
    input logic [8:0]   cpu_dcache_tag,

    // outputs to COMBINE stage
    input  logic        mem_ready,
    output logic [4:0]  mem_dest,
    output logic [31:0] mem_result,

    // Inputs from the AUX bus
    input logic         cpu_aux_rvalid,        // Read data is availible
    input logic [31:0]  cpu_aux_rdata,         // The read data.
    input logic [8:0]   cpu_aux_rtag,

    // Inputs from divider unit
    input  logic        div_valid,           // result is complete
    input  logic [31:0] div_result,
    input  logic [4:0]  div_dest_reg,

    // Inputs from FPU
    input  logic        fpu_valid,           // result is complete
    input  logic [31:0] fpu_result,
    input  logic [4:0]  fpu_dest_reg
);

logic        mem_valid, next_mem_valid;
logic [4:0]  next_mem_dest_reg;
logic [31:0] next_mem_result;
logic        skid1_valid, next_skid1_valid;
logic [4:0]  skid1_dest, next_skid1_dest;
logic [31:0] skid1_result, next_skid1_result;
logic        skid2_valid, next_skid2_valid;
logic [4:0]  skid2_dest, next_skid2_dest;
logic [31:0] skid2_result, next_skid2_result;

logic       error_skid_overflow;

logic [31:0] data;
logic [4:0]  dest;

logic [31:0] aux_data;
logic [4:0]  aux_dest;

always_comb begin
    // Defaults - hold values
    next_mem_valid      = mem_valid;
    next_mem_dest_reg   = mem_dest;
    next_mem_result     = mem_result;
    next_skid1_valid    = skid1_valid;
    next_skid1_dest     = skid1_dest;
    next_skid1_result   = skid1_result;
    next_skid2_valid    = skid2_valid;
    next_skid2_dest     = skid2_dest;
    next_skid2_result   = skid2_result;
    error_skid_overflow = 1'b0;

    // If the combine stage is ready shift data along
    if (mem_ready) begin
        next_mem_valid    = skid1_valid;
        next_mem_dest_reg = skid1_dest;
        next_mem_result   = skid1_result;
        next_skid1_valid  = skid2_valid;
        next_skid1_dest   = skid2_dest;
        next_skid1_result = skid2_result;
        next_skid2_valid  = 1'b0;
        next_skid2_dest   = 5'b0;
        next_skid2_result = 32'bx;
    end

    // If there is new data from the memif extract it
    if (cpu_dcache_rvalid) begin
        dest = cpu_dcache_tag[4:0];
        case (cpu_dcache_tag[8:5])
            4'b0000: data = {{24{cpu_dcache_rdata[7]}},cpu_dcache_rdata[7:0]};
            4'b0001: data = {{24{cpu_dcache_rdata[15]}},cpu_dcache_rdata[15:8]};
            4'b0010: data = {{24{cpu_dcache_rdata[23]}},cpu_dcache_rdata[23:16]};
            4'b0011: data = {{24{cpu_dcache_rdata[31]}},cpu_dcache_rdata[31:24]};
            4'b0100: data = {{16{cpu_dcache_rdata[15]}},cpu_dcache_rdata[15:0]};
            4'b0110: data = {{16{cpu_dcache_rdata[31]}},cpu_dcache_rdata[31:16]};
            4'b1000: data = cpu_dcache_rdata;
            default:data = 32'bx; // Shouldn't happen
        endcase
    end else begin
        data = 32'bx;
        dest = 5'b0;
    end

    // If there is new data from the aux bus extract it
    if (cpu_aux_rvalid) begin
        aux_dest = cpu_aux_rtag[4:0];
        case (cpu_aux_rtag[8:5])
            4'b0000: aux_data = {{24{cpu_aux_rdata[7]}},cpu_aux_rdata[7:0]};
            4'b0001: aux_data = {{24{cpu_aux_rdata[15]}},cpu_aux_rdata[15:8]};
            4'b0010: aux_data = {{24{cpu_aux_rdata[23]}},cpu_aux_rdata[23:16]};
            4'b0011: aux_data = {{24{cpu_aux_rdata[31]}},cpu_aux_rdata[31:24]};
            4'b0100: aux_data = {{16{cpu_aux_rdata[15]}},cpu_aux_rdata[15:0]};
            4'b0110: aux_data = {{16{cpu_aux_rdata[31]}},cpu_aux_rdata[31:16]};
            4'b1000: aux_data = cpu_aux_rdata;
            default: aux_data = 32'bx; // Shouldn't happen
        endcase
    end else begin
        aux_data = 32'bx;
        aux_dest = 5'b0;
    end


    // If there is new data, add it to the pipeline
    if (cpu_dcache_rvalid) begin
        // New data from the dcache
        if (!next_mem_valid) begin
            next_mem_valid    = 1'b1;
            next_mem_dest_reg = dest;
            next_mem_result   = data;
        end else if (!next_skid1_valid) begin
            next_skid1_valid  = 1'b1;
            next_skid1_dest   = dest;
            next_skid1_result = data;
        end else if (!next_skid2_valid) begin
            next_skid2_valid  = 1'b1;
            next_skid2_dest   = dest;
            next_skid2_result = data;
        end else if (dest!=5'b0) begin        
            // No space in the pipeline for the new data
            error_skid_overflow = 1'b1;
        end
    end

    if (cpu_aux_rvalid) begin
        // New data from the aux bus
        if (!next_mem_valid) begin
            next_mem_valid    = 1'b1;
            next_mem_dest_reg = aux_dest;
            next_mem_result   = aux_data;
        end else if (!next_skid1_valid) begin
            next_skid1_valid  = 1'b1;
            next_skid1_dest   = aux_dest;
            next_skid1_result = aux_data;
        end else if (next_skid2_valid) begin
            next_skid2_valid  = 1'b1;
            next_skid2_dest   = aux_dest;
            next_skid2_result = aux_data;
        end else if (aux_dest!=5'b0) begin        
            // No space in the pipeline for the new data
            error_skid_overflow = 1'b1;
        end
    end

    if (fpu_valid) begin
        // New data from the FPU
        if (!next_mem_valid) begin
            next_mem_valid    = 1'b1;
            next_mem_dest_reg = fpu_dest_reg;
            next_mem_result   = fpu_result;
        end else if (!next_skid1_valid) begin
            next_skid1_valid  = 1'b1;
            next_skid1_dest   = fpu_dest_reg;
            next_skid1_result = fpu_result;
        end else if (!next_skid2_valid) begin
            next_skid2_valid  = 1'b1;
            next_skid2_dest   = fpu_dest_reg;
            next_skid2_result = fpu_result;
        end else if (fpu_dest_reg!=5'b0) begin        
            // No space in the pipeline for the new data
            error_skid_overflow = 1'b1;
        end
    end

    if (div_valid) begin
        // New data from the divider unit
        if (!next_mem_valid) begin
            next_mem_valid    = 1'b1;
            next_mem_dest_reg = div_dest_reg;
            next_mem_result   = div_result;
        end else if (!next_skid1_valid) begin
            next_skid1_valid  = 1'b1;
            next_skid1_dest   = div_dest_reg;
            next_skid1_result = div_result;
        end else if (!next_skid2_valid) begin
            next_skid2_valid  = 1'b1;
            next_skid2_dest   = div_dest_reg;
            next_skid2_result = div_result;
        end else if (div_dest_reg!=5'b0) begin        
            // No space in the pipeline for the new data
            error_skid_overflow = 1'b1;
        end
    end

    // reset
    if (reset) begin
        next_mem_dest_reg   = 5'b0;
        next_mem_result     = 32'bx;
        next_skid1_dest     = 5'b0;
        next_skid1_result   = 32'bx;
        next_skid2_dest     = 5'b0;
        next_skid2_result   = 32'bx;
        error_skid_overflow = 1'b0;
    end

end


always_ff @(posedge clock) begin
    skid1_valid  <= next_skid1_valid;
    skid1_dest   <= next_skid1_dest;
    skid1_result <= next_skid1_result;
    skid2_valid  <= next_skid2_valid;
    skid2_dest   <= next_skid2_dest;
    skid2_result <= next_skid2_result;
    mem_valid    <= next_mem_valid;
    mem_dest     <= next_mem_dest_reg;
    mem_result   <= next_mem_result;
end

// synopsys translate_off
always_ff @(posedge clock) begin
    if (error_skid_overflow) begin
        $display("ERROR: CPU read path skid buffer overflow");
        $stop;
    end
end
// synopsys translate_on

endmodule