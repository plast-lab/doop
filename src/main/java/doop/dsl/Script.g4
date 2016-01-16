grammar DLScript;

@header {
package doop.dsl;
} 

program
	: topLevelPrimitive* ;

topLevelPrimitive
	: defineStage
	| applyStage
	| carryStage
	| finalize_
	| deltaLogic
	;


defineStage
	: STAGE IDENTIFIER stageBlock ; 

stageBlock
	: '{' stagePrimitive+ '}' ;

stagePrimitive
	: ECHO STRING
	| START_TIME
	| END_TIME
	| LOGIC IDENTIFIER
	;

applyStage
	: APPLY '{' stagePrimitive+ '}'
	| APPLY IDENTIFIER
	;

carryStage
	: CARRY ;

finalize_
	: FINALIZE ;

deltaLogic
	: DELTA IDENTIFIER ;


// Lexer

STAGE
	: 'stage' ;
APPLY
	: 'apply' ;
CARRY
	: 'carry' ;
FINALIZE
	: 'finalize' ;
DELTA
	: 'delta' ;
ECHO
	: 'echo' ;
LOGIC
	: 'logic' ;
START_TIME
	: 'startTime' ;
END_TIME
	: 'endTime' ;


STRING
	: '"' ~["]* '"' ; 

IDENTIFIER
	: [a-zA-Z_\-0-9.]+ ;


LINE_COMMENT
	: '//' ~[\r\n]* -> skip ;

BLOCK_COMMENT
	: '/*' .*? '*/' -> skip ;

WHITE_SPACE
	: [ \t\r\n]+ -> skip ;
