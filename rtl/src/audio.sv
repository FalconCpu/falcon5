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

// memory-mapped registers
logic [25:0]  reg_address[0:7];
logic [25:0]  reg_length[0:7];
logic [7:0]   reg_volume_left[0:7];   // Volume control (0-256)
logic [7:0]   reg_volume_right[0:7];  // Volume control (0-256)
logic [25:0]  reg_loop_address[0:7]; // Address to loop back to
logic [25:0]  reg_loop_length[0:7];  // Length of loop section in samples
logic [16:0]  reg_pitch[0:7];         // Pitch control
logic [25:0]  reg_write_addr;         // Address for writeback
logic [25:0]  reg_write_length;        // count of the number of words still to write

logic        sample_strobe;

logic signed [17:0] sample_left, next_sample_left;      // Includes 2 extra bits for overflow
logic signed [17:0] sample_right, next_sample_right;

logic [3:0]  state, next_state;
logic [17:0] phase_acc[0:7], next_phase_acc[0:7];

// Multiplier signals
logic        [15:0] mult_a;
logic        [7:0]  mult_b;
logic        [23:0] mult_result;

// Audio memory read interface
logic         mem_request, next_mem_request;   // A transaction is ready to be processed
logic [25:0]  mem_address, next_mem_address;   // Address to read
logic         mem_valid;                       // Memory read has completed
logic         mem_write, next_mem_write;
logic [15:0]  mem_data;                        // Data read#
logic [31:0]  next_wdata;

// Current position in audio stream
logic [25:0]  current_address[0:7], next_current_address[0:7];
logic [25:0]  words_remaining[0:7], next_words_remaining[0:7];
logic signed [15:0]  current_sample[0:7], next_current_sample[0:7];
logic signed [15:0]  prev_sample[0:7], next_prev_sample[0:7];

logic [15:0]  interpolated_sample, next_interpolated_sample;
logic [2:0]   current_channel, next_current_channel;
logic [7:0]   start_voice, next_start_voice;

logic [1:0]   phase_msb;

localparam START=4'h0,
           INC_PHASE =4'h1,
           FETCH_SAMPLE=4'h2,
           WAIT_SAMPLE=4'h3,
           INTERPOLATE_1 = 4'h4, 
           INTERPOLATE_2 = 4'h5,
           INTERPOLATED = 4'h6,
           MUL_LEFT=4'h7, 
           MUL_RIGHT=4'h8, 
           COMPLETE =4'h9,
           NEXT_VOICE=4'ha,
           WRITEBACK=4'hb,
           DONE=4'hf;

// clamp to signed 16-bit range
localparam signed [17:0] MAX_16 = 18'sd32767;
localparam signed [17:0] MIN_16 = -18'sd32768;


integer i;



always_comb begin
    next_state = state;
    next_sample_left = sample_left;
    next_sample_right = sample_right;
    mult_a = 16'h0;
    mult_b = 8'h0;
    next_mem_request = 1'b0;
    next_mem_address = mem_address;
    next_wdata = sdram_wdata;
    next_mem_write = 1'b0;

    for (i=0; i<8; i=i+1) begin
        next_current_address[i] = current_address[i];
        next_words_remaining[i] = words_remaining[i];
        next_current_sample[i] = current_sample[i];
        next_prev_sample[i] = prev_sample[i];
        next_phase_acc[i] = phase_acc[i];
    end
    next_interpolated_sample = interpolated_sample;
    next_current_channel = current_channel;
    next_start_voice = start_voice;
    phase_msb = phase_acc[current_channel][17:16];  // Top two bits of phase accumulator

    case (state)
        START: begin  // Start calculating the next sample
            next_sample_left = 18'h0;
            next_sample_right = 18'h0;
            next_current_channel = 0;
            next_wdata = 0;
            next_state = INC_PHASE;
        end

        INC_PHASE: begin  // Increment phase accumulator
            next_phase_acc[current_channel] = phase_acc[current_channel] + reg_pitch[current_channel];
            // Look for new data to play
            if (start_voice[current_channel]) begin
                next_start_voice[current_channel] = 1'b0;
                next_words_remaining[current_channel] = reg_length[current_channel];
                next_current_address[current_channel] = reg_address[current_channel];
                next_phase_acc[current_channel] = 18'h10000;
                next_prev_sample[current_channel] = 16'h0;
                next_current_sample[current_channel] = 16'h0;
            end else if (words_remaining[current_channel]==0 && next_current_address[current_channel]!=0) begin
                // we were playing a sample but reached the end. Jump to the loop point
                next_words_remaining[current_channel] = reg_loop_length[current_channel];
                next_current_address[current_channel] = reg_loop_address[current_channel];
            end

            if (next_words_remaining[current_channel]==0) begin
                next_state = NEXT_VOICE; // No more data to play
            end else begin
                next_state = FETCH_SAMPLE;
            end
        end

        FETCH_SAMPLE: begin  // Request the next sample from memory
            if (phase_msb!=2'b0) begin
                next_phase_acc[current_channel] = phase_acc[current_channel] - 18'h10000;  // Clear top bits
                next_mem_request = 1'b1;
                next_mem_address = current_address[current_channel];
                next_words_remaining[current_channel] = words_remaining[current_channel] - 1'b1;
                next_current_address[current_channel] = current_address[current_channel] + 1'b1; // Advance to the next sample
                next_prev_sample[current_channel] = current_sample[current_channel];
                next_state = WAIT_SAMPLE;
            end else 
                next_state = INTERPOLATE_1; // No sample needed this cycle
        end

        WAIT_SAMPLE: begin  // Wait for the sample to arrive
            next_mem_request = !mem_valid; // Keep request high until valid
            if (mem_valid) begin
                next_current_sample[current_channel] = mem_data;
                if (phase_msb==2'b00)
                    next_state = INTERPOLATE_1;
                else
                    next_state = FETCH_SAMPLE; // We need to skip a sample
            end else begin
                next_state = WAIT_SAMPLE; // Stay here until sample arrives
            end
        end

        INTERPOLATE_1: begin  // First stage of linear interpolation
            mult_a = prev_sample[current_channel];
            mult_b = ~phase_acc[current_channel][15:8]; // 1 - fraction
            next_state = INTERPOLATE_2;
        end

        INTERPOLATE_2: begin  // Second stage of linear interpolation
            next_interpolated_sample = mult_result[23:8];
            mult_a = current_sample[current_channel];
            mult_b = phase_acc[current_channel][15:8]; // fraction
            next_state = INTERPOLATED;
        end

        INTERPOLATED: begin  // Finish interpolation
            next_interpolated_sample = interpolated_sample + (mult_result[23:8]);
            next_state = MUL_LEFT;
        end

        MUL_LEFT: begin   // Multiply left channel sample by left volume
            mult_a = interpolated_sample;
            mult_b = reg_volume_left[current_channel];
            next_state = MUL_RIGHT;
        end

        MUL_RIGHT: begin   // Add to left channel sample, multiply right channel sample by right volume
            next_sample_left = sample_left + {mult_result[23],mult_result[23],mult_result[23:8]}; // Sign-extend
            mult_a = interpolated_sample;
            mult_b = reg_volume_right[current_channel];
            next_state = COMPLETE;

            // Capture outputs for writeback
            if (current_channel==0)   next_wdata[7:0]   = mult_result[23:16];
            if (current_channel==1)   next_wdata[15:8]  = mult_result[23:16];
            if (current_channel==2)   next_wdata[23:16] = mult_result[23:16];
            if (current_channel==3)   next_wdata[31:24] = mult_result[23:16];
        end

        COMPLETE: begin  // Add to right channel sample
            next_sample_right = sample_right + {mult_result[23],mult_result[23],mult_result[23:8]}; // Sign-extend            
            next_state = NEXT_VOICE;
        end

        NEXT_VOICE: begin
            if (current_channel==7)
                next_state = WRITEBACK;
            else begin
                next_current_channel = current_channel + 1'b1;
                next_state = INC_PHASE;
            end
        end

        WRITEBACK: begin
            next_mem_request = reg_write_length != 0;
            next_mem_write = 1'b1;
            next_mem_address = reg_write_addr;
            next_state = DONE;
        end 

        DONE: begin  // All channels processed - wait for I2S to consume samples
            // Saturate outputs to 16 bits
            if (next_sample_left > MAX_16)
                next_sample_left = MAX_16;
            else if (next_sample_left < MIN_16)
                next_sample_left = MIN_16;
            if (next_sample_right > MAX_16)
                next_sample_right = MAX_16;
            else if (next_sample_right < MIN_16)
                next_sample_right = MIN_16;
        end



        default: begin
            // Do nothing
        end
    endcase

    // If reset or we have just consumed a sample, go back to state 0
    if (sample_strobe)
        next_state = START;

    if (reset) begin
        next_state = START;
        next_sample_left = 18'h0;
        next_sample_right = 18'h0;
        next_mem_request = 1'b0;
        next_mem_address = 26'h0;
        for (i=0; i<8; i=i+1) begin
            next_current_address[i] = 26'h0;
            next_words_remaining[i] = 26'h0;
            next_current_sample[i] = 16'h0;
            next_prev_sample[i] = 16'h0;
            next_phase_acc[i] = 18'h0;
        end
        next_current_channel = 3'h0;
    end
end


always_ff @(posedge clock) begin
    hwregs_rdata <= 32'h0000_0000;
    sample_left <= next_sample_left;
    sample_right <= next_sample_right;
    state <= next_state;
    mult_result <= {{8{mult_a[15]}},mult_a} * {1'b0,mult_b};
    mem_request <= next_mem_request;
    mem_address <= next_mem_address;
    mem_write <= next_mem_write;
    sdram_wdata <= next_wdata;

    for (i=0; i<8; i=i+1) begin
        current_address[i] <= next_current_address[i];
        words_remaining[i] <= next_words_remaining[i];
        current_sample[i] <= next_current_sample[i];
        phase_acc[i] <= next_phase_acc[i];
       prev_sample[i] <= next_prev_sample[i];
    end
    interpolated_sample <= next_interpolated_sample;
    current_channel <= next_current_channel;
    start_voice <= next_start_voice;

    // Handle register writes
    if (hwregs_request && hwregs_write) begin
        casez (hwregs_addr)
            16'b0000_0010_???0_0000: reg_address[hwregs_addr[7:5]] <= hwregs_wdata[25:0];      // AUDIO_ADDRESS register
            16'b0000_0010_???0_0100: begin
                reg_length[hwregs_addr[7:5]] <= hwregs_wdata[25:0];       // AUDIO_LENGTH register    
                start_voice[hwregs_addr[7:5]] <= 1'b1;  // Start playing the sound from the beginning
            end
            16'b0000_0010_???0_1000: begin
                reg_volume_left[hwregs_addr[7:5]]  <= hwregs_wdata[7:0];        // AUDIO_VOLUME_LEFT register
                reg_volume_right[hwregs_addr[7:5]] <= hwregs_wdata[23:16];      // AUDIO_VOLUME_RIGHT register
            end
            16'b0000_0010_???0_1100: reg_pitch[hwregs_addr[7:5]] <= hwregs_wdata[16:0];        // AUDIO_PITCH register
            16'b0000_0010_???1_0000: reg_loop_address[hwregs_addr[7:5]] <= hwregs_wdata[25:0]; // AUDIO_LOOP_ADDRESS register
            16'b0000_0010_???1_0100: reg_loop_length[hwregs_addr[7:5]] <= hwregs_wdata[25:0];  // AUDIO_LOOP_LENGTH register
            16'b0000_0010_1111_1000: reg_write_addr <= hwregs_wdata[25:0];
            16'b0000_0010_1111_1100: reg_write_length <= hwregs_wdata[25:0];
            default: begin  // Ignore writes to other addresses
            end
        endcase
    end

    // Handle register reads
    if (hwregs_request && !hwregs_write) begin
        casez (hwregs_addr)
            16'b0000_0010_???0_0000: hwregs_rdata <= {6'b0, reg_address[hwregs_addr[7:5]]};      // AUDIO_ADDRESS register
            16'b0000_0010_???0_0100: hwregs_rdata <= {6'b0, reg_length[hwregs_addr[7:5]]};       // AUDIO_LENGTH register    
            16'b0000_0010_???0_1000: hwregs_rdata <= {8'b0,reg_volume_right[hwregs_addr[7:5]],8'b0,reg_volume_left[hwregs_addr[7:5]]}; // AUDIO_VOLUME_LEFT register
            16'b0000_0010_???0_1100: hwregs_rdata <=  {15'b0, reg_pitch[hwregs_addr[7:5]]};        // AUDIO_PITCH register
            16'b0000_0010_???1_0000: hwregs_rdata <= {6'b0, reg_loop_address[hwregs_addr[7:5]]}; // AUDIO_LOOP_ADDRESS register
            16'b0000_0010_???1_0100: hwregs_rdata <= {6'b0, reg_loop_length[hwregs_addr[7:5]]};
            16'b0000_0010_1111_1000: hwregs_rdata <= {6'b0, reg_write_addr};
            16'b0000_0010_1111_1100: hwregs_rdata <= {6'b0, reg_write_length};
            default: begin  // Ignore reads from other addresses
                hwregs_rdata <= 32'h0000_0000;
            end
        endcase
    end

    // Increment memory write address
    if (mem_request && mem_write) begin
        reg_write_addr <= reg_write_addr + 26'd4;
        reg_write_length <= reg_write_length - 1'd1;    
    end

    if (reset) begin
        for (i=0; i<8; i=i+1) begin
            reg_address[i] <= 26'h0;
            reg_length[i] <= 26'h0;
            reg_volume_left[i] <= 8'hFF;
            reg_volume_right[i] <= 8'hFF;
            reg_pitch[i] <= 17'h10000; // Normal pitch
        end
        reg_write_length <= 0;
        start_voice <= 0;
    end
end

// I2S output instance
i2s_output  i2s_output_inst (
    .clock(clock),
    .reset(reset),
    .sample_left(sample_left[15:0]),
    .sample_right(sample_right[15:0]),
    .sample_strobe(sample_strobe),      // Driven high for one clock when samples have been consumed
    .AUD_MCLK(AUD_XCK),
    .AUD_BCLK(AUD_BCLK),
    .AUD_LRCLK(AUD_DACLRCK),
    .AUD_DACDAT(AUD_DACDAT)
  );

audio_readmem  audio_readmem_inst (
    .clock(clock),
    .reset(reset),
    .current_channel(current_channel),
    .mem_request(mem_request),
    .mem_write(mem_write),
    .mem_address(mem_address),
    .mem_valid(mem_valid),
    .mem_data(mem_data),
    .sdram_request(sdram_request),
    .sdram_write(sdram_write),
    .sdram_ready(sdram_ready),
    .sdram_address(sdram_address),
    .sdram_rvalid(sdram_rvalid),
    .sdram_raddress(sdram_raddress),
    .sdram_rdata(sdram_rdata),
    .sdram_complete(sdram_complete)
  );

wire unused_ok = &{ 1'b0, hwregs_wdata[31:24], mult_result[7:0]};

endmodule