#!/usr/bin/env python
import sys
import r2pipe
import re

debug = False

def main():
    argsNum = len(sys.argv)
    if argsNum < 3 or sys.argv[1] == '-h' or sys.argv[1] == '-help':
        usage()

    mode = sys.argv[1]
    r = r2pipe.open(sys.argv[2], flags=['-e anal.timeout=600'])
    if mode == 'strings' and argsNum == 4:
        with open(sys.argv[3], 'w') as out:
            findStrings(r, out)
    elif mode == 'sections' and argsNum == 4:
        with open(sys.argv[3], 'w') as out:
            sections(r, out)
    elif mode == 'epoints' and argsNum == 4:
        with open(sys.argv[3], 'w') as out:
            entryPoints(r, out)
    elif mode == 'info' and argsNum == 4:
        with open(sys.argv[3], 'w') as out:
            info(r, out)
    elif mode == 'xrefs' and argsNum == 5:
        with open(sys.argv[3], 'r') as strings:
            with open(sys.argv[4], 'w') as out:
                xrefs(r, strings.readlines(), out)
    else:
        usage()
    r.quit()

def usage():
    print('Usage: radare.py MODE BINARY [STRINGS_FILE] [OUT_FILE]')
    print('')
    print('  MODE                   one of "strings", "xrefs", "sections", "epoints", "info"')
    print('  BINARY                 a native code binary')
    print('  LOCALIZE_STRINGS_FILE  a file containing strings to localize')
    print('                         (expensive analysis)')
    print('  OUT_FILE               the file to use for output in localization mode')
    print('')
    print('Examples:')
    print('  radare.py strings lib.so        # show all strings in library')
    print('  radare.py xrefs lib.so in out   # show containing function for strings in file \'in\', write results to file \'out\'')
    print('  radare.py sections lib.so out   # read library sections, write them to file \'out\'')
    print('  radare.py epoints lib.so out    # read library entry points, write them to file \'out\'')
    sys.exit(0)

def rCmd(r, c):
    if debug:
        print("Radare command: " + c)
    return r.cmd(c)

# Calculate string xrefs.
def xrefs(r, strings, out):
    print('Running xref analysis (output: ' + out.name + ')...')

    # analyze code using ESIL to find data references from code
    rCmd(r, 'aa')
    rCmd(r, 'aae')
    rCmd(r, 'aav')
    rCmd(r, 'aan')

    # Localize each string by (a) finding its offset and (b) following its xrefs.
    for line in strings:
        line = stripEOL(line)
        spaceIdx = line.find(" ")
        if spaceIdx == -1:
            print("ERROR, malformed line: " + line)
            continue
        addr = line[0:spaceIdx]
        s = line[(spaceIdx+1):]
        refs = rCmd(r, 'axt ' + addr)
        for ref in refs.splitlines():
            refParts = ref.split()
            func = refParts[0].encode('utf-8')
            codeAddr = refParts[1].encode('utf-8')
            info = 'STRING_LOC:' + func + '\t' + codeAddr + '\t' + s
            if (debug):
                print(info)
            out.write(info + '\n')

def stripEOL(s):
    if s.endswith('\n'):
        return s[:-1]
    else:
        return s

# Strings mode:  list strings in the binary.
def findStrings(r, out):
    print('Reading binary strings...')
    # if you want to analyze binary file
    # rCmd(r, 'aaaa')
    processTable(r, out, 'iz', 3, 8, stringProc)

def stringProc(lineParts):
        vaddr = lineParts[2]
        string = ''.join(lineParts[7:])
        return 'STRING:' + vaddr + '\t' + string.encode('utf-8')

# Sections mode:  list binary sections.
def sections(r, out):
    print('Reading binary sections...')
    processTable(r, out, 'iS', 3, 7, sectionProc)

def sectionProc(lineParts):
    offset = lineParts[1]
    size = lineParts[2]
    vaddr = lineParts[3]
    sectionName = ''.join(lineParts[6:]).encode('utf-8')
    return 'SECTION:' + sectionName + '\t' + vaddr + '\t' + size + '\t' + offset

# Sections mode: list binary sections.
def entryPoints(r, out):
    print('Reading binary entry points...')
    processTable(r, out, 'iE', 3, 7, entryPointProc)

def entryPointProc(lineParts):
    vaddr = lineParts[2]
    name = lineParts[6]
    return 'ENTRY_POINT:' + vaddr + '\t' + name

# Show binary information.
def info(r, out):
    print('Reading binary information...')
    processTable(r, out, 'i', 0, 2, infoProc)

def infoProc(lineParts):
    key = lineParts[0]
    value = lineParts[1]
    return 'INFO:' + key + '\t' + value

def processTable(r, out, cmd, IGNORE_FIRST, COLUMNS, proc):
    stringLines = rCmd(r, cmd)
    flagCounter = 0
    for stringLine in stringLines.splitlines():
        if debug:
            print stringLine.encode('utf-8')
        flagCounter += 1
        if flagCounter < IGNORE_FIRST:
            continue;
        lineParts = stringLine.split()
        if len(lineParts) < COLUMNS:
            if debug:
                print("IGNORED: " + stringLine)
            continue
        try:
            info = proc(lineParts)
            if debug:
                print(info)
            out.write(info + '\n')
        except:
            if debug:
                print("IGNORED: " + stringLine)

if __name__== "__main__":
	main()
