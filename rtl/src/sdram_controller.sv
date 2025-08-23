`timescale 1ns/1ps

module sdram_controller(
    input  logic clock,
    input  logic reset,

    // Arbiter interface
    input  logic [2:0]  sdram_request,      // Which bus master requests SDRAM access
    output logic        sdram_ready,        // SDRAM has responded to the request.
    input  logic [25:0] sdram_address,      // Address of data to read/write
    input  logic        sdram_write,        // 1 = write, 0 = read
    input  logic        sdram_burst,        // 1 = burst, 0 = single
    input  logic [3:0]  sdram_wstrb,        // For a write, which bytes to write.
    input  logic [31:0] sdram_wdata,        // Data to write / tag for read
    output logic [8:0]  sdram_rtag,         // Tag of the request that was just completed
    output logic [31:0] sdram_rdata,        // Data read from SDRAM
    output logic [2:0]  sdram_rvalid,        // Which bus master to send data to
    output logic        sdram_complete,     // SDRAM controller is done with the current burst
  
    // SDRAM interface
    output logic [12:0] DRAM_ADDR,
	output logic  [1:0] DRAM_BA,
	output logic        DRAM_CAS_N,
	output logic        DRAM_CKE,
	output logic        DRAM_CS_N,
	inout  logic [15:0] DRAM_DQ,
	output logic        DRAM_LDQM,
	output logic        DRAM_RAS_N,
	output logic        DRAM_UDQM,
	output logic        DRAM_WE_N
);

// SDRAM COMMANDS
`define CMD_NOP       3'b111
`define CMD_READ      3'b101
`define CMD_WRITE     3'b100
`define CMD_ACTIVE    3'b011
`define CMD_PRECHARGE 3'b010
`define CMD_REFRESH   3'b001
`define CMD_LOADMODE  3'b000

// CONTROLLER STATES
`define STATE_INITIALIZE  3'h0
`define STATE_READY       3'h1
`define STATE_READ        3'h2
`define STATE_WRITE       3'h3
`define STATE_PRECHARGE   3'h4
`define STATE_REFRESH     3'h5
`define STATE_ACTIVATE    3'h6
`define STATE_READ_BURST  3'h7

logic [2:0] this_state, next_state;
logic [12:0] next_addr;
logic [1:0]  next_bank;
logic [15:0] this_data, next_data;
logic        this_oe, next_oe;
logic [2:0]  next_cmd;
logic [1:0]  next_dqm;
logic [7:0]  count, next_count;

logic [3:0]  bank_open, next_bank_open;
logic [12:0] bank0_row, next_bank0_row;
logic [12:0] bank1_row, next_bank1_row;
logic [12:0] bank2_row, next_bank2_row;
logic [12:0] bank3_row, next_bank3_row;
logic [12:0] selected_bank_row;
logic        selected_bank_open;

// CPU transfers are 32 bits, but SDRAMs are 16 bits. So we need to capture the upper half of a 32 bit write
// to present to the SDRAM on the next clock cycle.
logic [15:0] this_wdata, next_wdata;
logic [1:0]  this_woe, next_woe;

// The sdram requires 8192 refreshes per 64ms
// 64ms / 8192 = 7.8125us
// Our clock is 100Mhz so we need one refresh every 780 cycles
logic [9:0] referesh_count, next_refresh_count;
logic       refresh_needed,  next_refresh_needed;


// Address mapping - the 26 bit address is split into 
// [1:0]    bytes within a word
// [10:2]   column address   addr[9:1]
// [12:11]  bank number
// [25:13]  row address

wire [12:0] addr_row  = sdram_address[25:13];
wire [1:0]  addr_bank = sdram_address[12:11];
wire [8:0]  addr_col  = sdram_address[10:2];


assign DRAM_CKE  = 1'b1;
assign DRAM_CS_N = 1'b0;
assign DRAM_DQ   = this_oe ? this_data : 16'hzzzz;

logic [1:0] write_pipeline, next_write_pipeline;        // Count clock cycles since last write
logic [14:0] read_pipeline, next_read_pipeline;         // Pipeline showing which master each slot belongs to.
logic [3:0] complete_pipeline, next_complete_pipeline;  // Pipeline showing which master each slot belongs to.
logic [2:0] next_read_master, this_read_master;
logic [8:0] next_burst_addr, this_burst_addr;
logic [8:0] next_tag, tag_0, tag_1, tag_2, tag_3, tag_4;              // delay line to align tags with the reads that triggered them


always_comb begin
    next_state = this_state;
    next_addr  = 13'hx;
    next_bank  = 2'bxx;
    next_data  = 16'hx;
    next_oe    = 1'b0;
    next_dqm   = (read_pipeline[5:0]!=0) ? 2'b00 : 2'bxx;
    next_cmd   = `CMD_NOP;
    next_bank_open = bank_open;
    next_bank0_row = bank0_row;
    next_bank1_row = bank1_row;
    next_bank2_row = bank2_row;
    next_bank3_row = bank3_row;
    next_read_master = this_read_master;
    sdram_ready  = 1'b0;
    sdram_complete = complete_pipeline[3]; 
	next_woe   = 2'bx;
	next_wdata = 16'bx;
	next_refresh_needed = refresh_needed;
    next_count = count+1'b1;        // Counter is reset when in STATE_READY, and incremented in all other states
    next_read_pipeline = {read_pipeline[11:0], 3'b0};
    next_complete_pipeline = {complete_pipeline[2:0], 1'b0};
    next_write_pipeline = {write_pipeline[0], 1'b0};
    next_burst_addr = this_burst_addr;
    next_tag = tag_0;


    // Timer for refreshing SDRAM
    next_refresh_count = referesh_count +1'b1;
    next_refresh_needed = refresh_needed;
    if (referesh_count == 780) begin
        next_refresh_count = 0;
        next_refresh_needed = 1'b1;
    end

    // Determine which row the selected bank is currently open at
    selected_bank_open = bank_open[addr_bank];
    selected_bank_row = (addr_bank==2'b00) ? bank0_row :
                        (addr_bank==2'b01) ? bank1_row :
                        (addr_bank==2'b10) ? bank2_row : 
                                             bank3_row;

    case (this_state) 
        `STATE_INITIALIZE: begin
            if (count==2) begin
                next_cmd = `CMD_PRECHARGE;
                next_addr = 13'h0400;
            end else if (count==8 || count==18 || count==28 || count==38 || count==48 || count==58 || count==68 || count==78)
                next_cmd = `CMD_REFRESH;
            else if (count==88) begin
                next_addr = 13'h0031;
                next_cmd = `CMD_LOADMODE;
            end else if (count==92) 
                next_state = `STATE_READY;
        end

        `STATE_READY: begin 
            next_count = 8'h00;

            if (refresh_needed) begin
                if (write_pipeline==0) 
                    next_state = `STATE_REFRESH;

            end else if (sdram_request!=0 && selected_bank_open && selected_bank_row!=addr_row && write_pipeline==0) begin
                // The addressed bank is not open at the row we want to access - precharge it
                next_cmd = `CMD_PRECHARGE;
                next_addr = 13'h0;
                next_bank = addr_bank;
                next_state = `STATE_PRECHARGE;

            end else if (sdram_request!=0 && !selected_bank_open) begin
                // The addressed bank is not open - open it
                next_cmd = `CMD_ACTIVE;
                next_addr = addr_row;
                next_bank = addr_bank;
                next_state = `STATE_ACTIVATE;
            end else if (sdram_request!=0 && selected_bank_open && selected_bank_row==addr_row) begin
                if (sdram_write && read_pipeline==0) begin
                    // The addressed bank is open at the row we want to access - write to it
                    next_cmd  = `CMD_WRITE;
                    next_write_pipeline[0] = 1'b1;
                    next_addr = {3'b0,addr_col,1'b0};
                    next_bank = addr_bank;
                    next_data = sdram_wdata[15:0];
                    next_dqm  = ~sdram_wstrb[1      :0];
                    next_oe   = 1'b1;
                    next_wdata = sdram_wdata[31:16];
                    next_woe   = sdram_wstrb[3      :2];
                    next_tag   = sdram_wdata[8:0];
                    sdram_ready  = 1'b1;
                    next_state = `STATE_WRITE;
                end else if (!sdram_write) begin
                    // The addressed bank is open at the row we want to access - read from it
                    next_cmd  = `CMD_READ;
                    next_addr = {3'b0,addr_col,1'b0};
                    next_bank = addr_bank;
                    next_dqm  = 0;
                    next_state = sdram_burst ? `STATE_READ_BURST : `STATE_READ;      // ***
                    next_tag   = sdram_wdata[8:0];
                    sdram_ready  = 1'b1;
                    next_read_pipeline[2:0] = sdram_request;
                    next_read_master = sdram_request;
                    next_complete_pipeline[0] = !sdram_burst;        // ***
                    next_burst_addr = addr_col;
                end
            end else if (sdram_request==0) begin
                // No requests - stay in ready state
                next_state = `STATE_READY;
                sdram_ready  = 1'b1;
            end
        end

        `STATE_READ: begin 
            next_dqm  = 0;
            next_state = `STATE_READY;
        end

        `STATE_READ_BURST: begin
            next_dqm  = 0;
            next_bank = DRAM_BA;
            if (count==31) begin
                next_state = `STATE_READY;
                next_complete_pipeline[0] = 1'b1;
            end
            else if (count[0]==1'b1) begin
                next_cmd = `CMD_READ;
                next_burst_addr = {this_burst_addr[8:4], this_burst_addr[3:0]+1'b1};
                next_addr = {3'b0,next_burst_addr,1'b0};
                next_read_pipeline[2:0] = this_read_master;
            end
        end



        `STATE_WRITE: begin 
            next_cmd = `CMD_NOP;
            //next_addr = {DRAM_ADDR[12:1],1'b1};
            //next_bank = DRAM_BA;
            next_data = this_wdata;
            next_oe   = 1'b1;
            next_dqm  = ~this_woe;
            next_state = `STATE_READY;
        end

        `STATE_PRECHARGE: begin 
            if (count==3)
                next_state = `STATE_READY;
        end

        `STATE_ACTIVATE: begin 
            if (count==2)
                next_state = `STATE_READY;
        end


        `STATE_REFRESH: begin 
            if (count==0) begin
                next_cmd = `CMD_PRECHARGE;
                next_addr = 13'h0400;
            end
            if (count==3) begin
                next_cmd   = `CMD_REFRESH;
                next_refresh_needed = 1'b0;
            end
            if (count==11)
                next_state = `STATE_READY;
        end
    endcase

    // Our local model of the SDRAM to keep track of which rows are open
    if (next_cmd == `CMD_PRECHARGE && next_addr[10]) 
        next_bank_open = 4'b0000;
    if (next_cmd == `CMD_PRECHARGE && next_addr[10]==0)
        next_bank_open[next_bank] = 1'b0;
    if (next_cmd == `CMD_ACTIVE && next_bank==2'b00) begin
        next_bank_open[0] = 1'b1;
        next_bank0_row = next_addr;
    end
    if (next_cmd == `CMD_ACTIVE && next_bank==2'b01) begin
        next_bank_open[1] = 1'b1;
        next_bank1_row = next_addr;
    end
    if (next_cmd == `CMD_ACTIVE && next_bank==2'b10) begin
        next_bank_open[2] = 1'b1;
        next_bank2_row = next_addr;
    end
    if (next_cmd == `CMD_ACTIVE && next_bank==2'b11) begin
        next_bank_open[3] = 1'b1;
        next_bank3_row = next_addr;
    end


    if (reset) begin
        next_state = `STATE_INITIALIZE;
        next_count = 8'h00;
        next_refresh_count = 10'h000;
        next_refresh_needed = 1'b0;
    end
end


always_ff @(posedge clock) begin
    DRAM_ADDR  <= next_addr;
    DRAM_BA    <= next_bank;
    DRAM_RAS_N <= next_cmd[2];
    DRAM_CAS_N <= next_cmd[1];
    DRAM_WE_N  <= next_cmd[0];
    DRAM_UDQM  <= next_dqm[1];
    DRAM_LDQM  <= next_dqm[0];
    this_data  <= next_data;
    this_oe    <= next_oe;
    this_state <= next_state;
    count      <= next_count;
    bank_open  <= next_bank_open;
    bank0_row  <= next_bank0_row;
    bank1_row  <= next_bank1_row;
    bank2_row  <= next_bank2_row;
    bank3_row  <= next_bank3_row;
    referesh_count <= next_refresh_count;
    refresh_needed <= next_refresh_needed;
    this_wdata <= next_wdata;
    this_woe <= next_woe;

    if (read_pipeline[11:9]!=0)
        sdram_rdata[15:0] <= DRAM_DQ;
    if (read_pipeline[14:12]!=0)   
        sdram_rdata[31:16] <= DRAM_DQ;
    sdram_rvalid <= read_pipeline[14:12];
    write_pipeline <= next_write_pipeline;
    read_pipeline <= next_read_pipeline;
    complete_pipeline <= next_complete_pipeline;
    this_burst_addr <= next_burst_addr;
    this_read_master <= next_read_master;
    tag_0 <= next_tag;
    tag_1 <= tag_0;
    tag_2 <= tag_1;
    tag_3 <= tag_2;
    tag_4 <= tag_3;
    sdram_rtag <= tag_4;
end

wire unused_ok = &{1'b0,sdram_address[1:0]};

endmodule