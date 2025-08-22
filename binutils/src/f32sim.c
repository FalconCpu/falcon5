#define _CRT_SECURE_NO_WARNINGS
#include <stdio.h>
#include <stdlib.h>
#include <windows.h>
#include "f32.h"

int line_number;
int* prog_mem;
unsigned int* data_mem;

int abort_on_exception = 0;

FILE* trace_file  = NULL;

static void load_program(string filename) {
    FILE* file = fopen(filename, "r");
    if (file == NULL)
        fatal("Can't open file '%s'", filename);

    char line[100];
    int program_size = 0;
    while (fgets(line, sizeof(line), file) != NULL)
        prog_mem[program_size++] = strtoul(line,0,16);
    fclose(file);
}


int main(int argc, char** argv) {
    prog_mem = my_malloc(65536);
    data_mem = my_malloc(64*1024*1024);

    string filename=0;

    for (int i=1; i<argc; i++) {
        if (strcmp(argv[i], "-a")==0)
            abort_on_exception = 1;
        else if (strcmp(argv[i], "-t")==0)
            trace_file = fopen("sim_traace.log", "w");
        else if (strcmp(argv[i], "-h")==0)
            printf("Usage: %s [-a] [-t] <filename>\n", argv[0]);
        else if (argv[i][0] == '-')
            fatal("unknown option '%s'", argv[i]);
        else if (filename==0)
            filename = argv[i];
        else
            fatal("too many arguments");
    }

    if (filename==0)
        fatal("no filename specified");

    load_program(filename);
    load_labels("asm.labels");
    execute();

    return 0;
}
