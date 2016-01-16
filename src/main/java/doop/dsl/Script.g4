grammar Script;

@header {
//package doop.dsl;
} 

program
	: topLevelPrimitive* ;

topLevelPrimitive
	: defineStage
	| applyStage
	| carryStage
	| finalize_
	| deltaLogic
	| defineCommand
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
	: APPLY stageBlock
	| APPLY IDENTIFIER
	;

carryStage
	: CARRY ;

finalize_
	: FINALIZE ;

deltaLogic
	: DELTA IDENTIFIER ;

defineCommand
	: COMMAND IDENTIFIER commandBlock ;

commandBlock
	: '{' commandPrimitive+ '}' ;

commandPrimitive
	: EXPORT (IDENTIFIER (AS IDENTIFIER)?)?
	| IMPORT (IDENTIFIER (AS IDENTIFIER)?)?
	| CMD
	;


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
COMMAND
	: 'command' ;
ECHO
	: 'echo' ;
LOGIC
	: 'logic' ;
START_TIME
	: 'startTime' ;
END_TIME
	: 'endTime' ;
EXPORT
	: 'export' ;
IMPORT
	: 'import' ;
AS
	: 'as' ;


CMD
	: '`' ~[`]* '`' ;

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
