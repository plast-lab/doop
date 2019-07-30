import sys
import r2pipe
import re

def main():
    r = r2pipe.open(sys.argv[1], flags=['-e anal.timeout=600'])
    # if you want to analyzze binary file
    # r.cmd('aaaa')
    stringLines = r.cmd('iz')
    flagCounter = 0
    for stringLine in stringLines.splitlines():
        flagCounter += 1
        if flagCounter < 3:
            continue;
        lineParts = stringLine.split()
        string = ''.join(lineParts[7:])
        print(string.encode('utf-8'))

if __name__== "__main__":
	main()
