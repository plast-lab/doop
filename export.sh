#!/bin/bash

outDir=out-facts

for rel in $*
do
   # do translation of relation names into Souffle convetional format
   relFname=`echo $rel | tr : _ `

   \rm -f $outDir/$relFname.csv
   bloxbatch -db last-analysis -keepDerivedPreds -exportCsv $rel -exportDataDir $outDir -exportDelimiter '\t'
   if [ -e $outDir/$relFname.csv ]
   then
     tail -n +2 $outDir/$relFname.csv > $outDir/$relFname.facts
	 \rm $outDir/$relFname.csv
   else
     echo $rel not exported!
   fi

   # For some relations, the above mysteriously fails. The code in export2.sh
   # is not complete (fails when there is a matching comma-pattern in
   # the relation) but should be sufficient in many cases.  

done
