`timescale 1ns/1ns
// Audio Register Map
// where X is 0..7 for 8 audio channels
// 0xE0000200     AUDIO_ADDRESS        start address of sample
// 0xE0000204     AUDIO_LENGTH         length of sample. Writing to this register initiates playback from the start address
// 0xE0000208     AUDIO_VOLUME         (right in high 16 bits, left in low 16 bits)
// 0xE000020C     AUDIO_PITCH          sample rate. 0x10000=48kHz, 0x8000=24kHz, 0x4000=12kHz etc
// 0xE0000210     AUDIO_LOOP_ADDRESS   address to loop back to 
// 0xE0000214     AUDIO_LOOP_LENGTH    length of loop section in samples (0 = no loop)
// The above registers repeat *8
// 0xE00002F8     AUDIO_WRITE_ADDR     Address for writeback
// 0xE00002FC     AUDIO_WRITE_LENGTH   Number of samples to writeback


module audio (
    input logic        clock,
    input logic        reset,
    input logic        tick,            // Start calculation for next sample

    // Memory-mapped register interface
    input  logic        hwregs_request,     // Request a read or write
    input  logic        hwregs_write,       // 1 = write, 0 = read
    input  logic [15:0] hwregs_addr,        // Address of data to read/write
    input  logic [31:0] hwregs_wdata,       // Data to write
    output logic [31:0] hwregs_rdata,       // Data read

    // I2S interface to audio codec
    output logic       AUD_XCK,      // MCLK
    output logic       AUD_BCLK,     // BCLK
    output logic       AUD_DACLRCK,  // LRCLK
    output logic       AUD_DACDAT,   // DACDAT

    // SDRAM interface (externally wired to always read bursts)
    output logic         sdram_request,  // Request memory
    output logic         sdram_write,
    output logic [31:0]  sdram_wdata,
    input  logic         sdram_ready,    // SDRAM is ready for a request
    output logic [25:0]  sdram_address,  // Address
    input  logic         sdram_rvalid,   // Read data valid
    input  logic [25:0]  sdram_raddress, // Address of read data
    input  logic [31:0]  sdram_rdata,    // Read data
    input  logic         sdram_complete  // Burst complete

);

// The first 8 registers are accebbible over hwregs
// The laster ones are internal only

localparam   REG_START_ADDR    = 4'h0,    
             REG_LENGTH        = 4'h1,
             REG_VOLUME        = 4'h2,   // left in bits [7:0], right in bits [23:16]
             REG_PITCH         = 4'h3,
             REG_LOOP_ADDR     = 4'h4,
             REG_LOOP_LENGTH   = 4'h5,
             REG_WRITE_ADDR    = 4'h6,
             REG_WRITE_LENGTH  = 4'h7,
             REG_CUR_ADDR      = 4'h8,    // current address
             REG_PHASE         = 4'h9,    // current phase accumulator
             REG_SAMPLES          = 4'hA,    // current and previous sample data
             REG_MEM_ADDR      = 4'hB,    // Data block currently in data ram
             REG_COUNT         = 4'hC,    // Number of samples remaining
             REG_NONE          = 4'hf;

localparam STATE_IDLE          = 5'h0,
           STATE_START         = 5'h1,
           STATE_FETCH_         = 5'h2,
           STATE_WAIT          = 5'h3,
           STATE_SAMPLE        = 5'h4,
           STATE_WRITEBACK     = 5'h5;



logic [2:0]  current_channel, next_current_channel;
logic [4:0]  state, next_state;
logic [7:0]  start_voice, next_start_voice;
logic [3:0]  sel_reg;
logic [15:0] mul_a;
logic [7:0]  mul_b;
logic [15:0] mul_result;
logic [31:0] reg_data, reg_wdata;
logic        reg_write;
logic [31:0] acc, next_acc;

always_comb begin
    next_state = state;
    next_current_channel = current_channel;
    next_start_voice = start_voice;
    mul_a = 16'hx;
    mul_b = 8'hx;
    reg_write = 1'b0;
    reg_wdata = 32'hx;
    next_acc = acc;
    sel_reg = REG_NONE;

    case(state) 
        STATE_IDLE: begin
            next_current_channel = 3'h0;
            if (tick)
                next_state = STATE_START;
        end

        STATE_START: begin
            // Look to see if this channel is starting
            if (start_voice[current_channel]) begin
                // Start it
                next_start_voice[current_channel] = 1'b0;
                sel_reg = REG_START_ADDR;
                next_state = STATE_START1;
            end else begin
                reg_sel = REG_COUNT;
                next_state = STATE_CHECK_COUNT;
            end
        end

        STATE_START1: begin
            // reg_data now has start address
            reg_sel = REG_CUR_ADDR;
            reg_wdata = reg_data;
            reg_write = 1'b1;
            next_state = STATE_START2;
        end

        STATE_START2: begin
            sel_reg = REG_PHASE;
            reg_wdata = 32'h0;
            reg_write = 1'b1;
            next_state = STATE_START3;
        end

        STATE_START3: begin
            // Clear the samples register
            sel_reg = REG_SAMPLES;
            reg_wdata = 32'h0;
            reg_write = 1'b1;
            next_state = STATE_4;
        end

        STATE_START4: begin
            // Fetch the length
            sel_reg = REG_LENGTH;
            next_state = STATE_START5;
        end

        STATE_START5: begin
            // store length in count
            reg_sel = REG_COUNT;
            reg_wdata = reg_data;
            reg_write = 1'b1;
            next_state = STATE_CHECK_COUNT;
        end


            



        default: begin
            next_state = STATE_IDLE;
        end
    endcase






end




endmodule