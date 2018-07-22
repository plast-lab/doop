#!/usr/bin/env python

# Generate a list of all Souffle declarations. Used for documentation purposes.

import datetime
import os
import sys

def parseNextDeclGen(content):
    position = 0
    nextDecl = 0
    while nextDecl != -1:
        nextDecl = content.find(".decl", position)
        if nextDecl != -1:
            nextRParen = content.find(")", nextDecl)
            if nextRParen != -1:
                decl = content[nextDecl:nextRParen+1]
                decl0 = ""
                for line in decl.split('\n'):
                    decl0 = decl0 + " " + line.strip()
                    # print("Found: " + str(nextDecl) + " -> " + str(nextRParen) + ": " + decl0)
                yield decl0
                position = nextRParen+1
            else:
                position = nextDecl+5

def startNewFile(outDoc, path):
    outDoc.write("<h2> " + path + " </h2>\n")
    
def printRule(outDoc, d):
    global ruleCount
    ruleCount = ruleCount + 1
    outDoc.write("<li>" + d + "</li>\n")

def listStart(outDoc):
    outDoc.write("<ul>\n")

def listEnd(outDoc):
    outDoc.write("</ul>\n")

def startDoc(outDoc):
    outDoc.write("""<html><body> <h1>Doop rules</h2>

    <p>This document lists all Souffl&eacute;-Datalog rules appearing
    in Doop logic. Note that some of these rules may be guarded by
    macros and thus be disabled in particular Doop runs.</p> """)

def endDoc(outDoc):
    outDoc.write("<p>Timestamp: " + str(datetime.datetime.now()) + "</p>")
    outDoc.write("</body></html>")
    
def genDocFromLogicDir(outDoc, logicDir):
    startDoc(outDoc)
    for root, subdirs, files in os.walk(logicDir):
        print("Processing directory: " + root)
        for fName in files:
            if fName.endswith(".dl"):
                path = os.path.join(root, fName)
                try:
                    content = ""
                    decls = []
                    # Ignore lines containing '//' (commented-out .decl lines).
                    with open(path, 'rb') as f:
                        for line in f.readlines():
                            if not ('//' in line):
                                content += line
                        #content = f.read()
                    for decl in parseNextDeclGen(content):
                        decls.append(decl)
                    if len(decls) > 0:
                        decls.sort()
                        startNewFile(outDoc, path.replace('souffle-logic/', ''))
                        listStart(outDoc)
                        for decl in decls:
                            printRule(outDoc, decl)
                        listEnd(outDoc)
                except IOError:
                    print("Ignoring " + fName + " due to error")
    endDoc(outDoc)

logicDir="souffle-logic"
outDocName="docs/rules.html"
ruleCount = 0

with open(outDocName, "w") as outDoc:
    genDocFromLogicDir(outDoc, logicDir)
print("Found " + str(ruleCount) + " rules.")
