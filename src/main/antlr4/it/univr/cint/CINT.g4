/*
 * CINT — linguaggio imperativo tipizzato staticamente, di impronta C-like.
 * Laboratorio di Linguaggi, A.A. 2025/2026.
 *
 * Funzionalita' avanzate implementate:
 *   - Funzioni (livello medio): sezione dichiarativa, ricorsione, parametri per valore e per riferimento
 *   - Strutture Dati: array a dimensione variabile, anche multidimensionali
 *   - Zucchero Sintattico: ++/--, assegnamenti composti, ternario, interpolazione di stringhe
 *   - Valutazione Pigra: assegnamenti 'lazy' con cattura per valore
 */
grammar CINT;

// ---------------------------------------------------------------- struttura

// Le funzioni vivono in una sezione dichiarativa iniziale, che non viene eseguita.
program : funDecl* com EOF ;

funDecl : retType ID LPAR params? RPAR block ;

retType : type | VOID ;

params : param (COMMA param)* ;

// 'ref' seleziona il passaggio per riferimento; senza, il passaggio e' per valore.
param : REF? type ID ;

type : TYPE (LBRACK RBRACK)* ;

block : LBRACE com? RBRACE ;

// ---------------------------------------------------------------- comandi
//
// Un comando e' una sequenza di comandi elementari, che si dividono in due specie:
//
//   - i comandi SEMPLICI (assegnamenti, print, return, ...) vanno separati dal ';';
//   - i comandi CHIUSI da una graffa (if, while, try, blocco) no: come in C la '}' segna da se' la
//     fine del comando, quindi 'if (c) { ... } print x' e' corretto senza ';' in mezzo.
//
// Il ';' e' un separatore, non un terminatore: l'ultimo comando di una sequenza non lo richiede.
// Un ';' superfluo e' tollerato dopo un comando qualunque (anche dopo '}', o prima di '}'), ma
// solo uno: ';;' resta un errore, perche' in CINT il comando che non fa niente si scrive 'nop'.
com : simpleCom (SEMICOLON com?)?
    | closedCom SEMICOLON? com?
    ;

simpleCom : type ID (ASSIGN exp)?                                 # decl
          | LAZY type ID ASSIGN exp                               # lazyDecl
          | lvalue ASSIGN exp                                     # assign
          | lvalue op=(ADD_A | SUB_A | MUL_A | DIV_A | MOD_A) exp # compoundAssign
          | lvalue op=(INCR | DECR)                               # postCrementCom
          | op=(INCR | DECR) lvalue                               # preCrementCom
          | THROW exp                                             # throw
          | RETURN exp?                                           # return
          | PRINT exp                                             # print
          | ID LPAR args? RPAR                                    # callCom
          | NOP                                                   # nop
          ;

closedCom : IF LPAR exp RPAR block                                # if
          | IF LPAR exp RPAR block ELSE block                     # ifElse
          | WHILE LPAR exp RPAR block                             # while
          | TRY block CATCH LPAR ID RPAR block                    # tryCatch
          | block                                                 # blockCom
          ;

// Bersaglio assegnabile: una variabile, oppure una cella di array.
lvalue : ID                         # varLvalue
       | lvalue LBRACK exp RBRACK   # cellLvalue
       ;

// ---------------------------------------------------------------- espressioni
//
// L'ordine delle alternative fissa la precedenza: piu' in alto, piu' stretto il legame.

exp : num                                           # numeric
    | BOOL                                          # boolean
    | CHAR                                          # character
    | STRING                                        # string
    | LBRACK args? RBRACK                           # arrayLit
    | NEW TYPE (LBRACK exp RBRACK)+                 # arrayNew
    | LPAR exp RPAR                                 # parExp
    | ID LPAR args? RPAR                            # call
    | LEN LPAR exp RPAR                             # len
    | TOSTR LPAR exp RPAR                           # toStr
    | exp LBRACK exp RBRACK                         # index
    | <assoc=right> exp POW exp                     # pow
    | lvalue op=(INCR | DECR)                       # postCrement
    | op=(INCR | DECR) lvalue                       # preCrement
    | LPAR TYPE RPAR exp                            # cast
    | NOT exp                                       # not
    | SUB exp                                       # neg
    | exp op=(MUL | DIV | MOD) exp                  # mulDivMod
    | exp op=(ADD | SUB) exp                        # addSub
    | exp op=(LT | LEQ | GEQ | GT) exp              # cmpExp
    | exp op=(EQQ | NEQ) exp                        # eqExp
    | exp AND exp                                   # and
    | exp OR exp                                    # or
    | <assoc=right> exp QUESTION exp COLON exp      # ternary
    | ID                                            # id
    ;

args : exp (COMMA exp)* ;

num : INT   # intNum
    | DEC   # decNum
    ;

// ---------------------------------------------------------------- lessico

// operatori composti e crementi: token piu' lunghi, quindi vincono sul match massimale
ADD_A : '+=' ;
SUB_A : '-=' ;
MUL_A : '*=' ;
DIV_A : '/=' ;
MOD_A : '%=' ;
INCR  : '++' ;
DECR  : '--' ;

ADD : '+' ;
SUB : '-' ;
MUL : '*' ;
DIV : '/' ;
MOD : '%' ;
POW : '^' ;

EQQ : '==' ;
NEQ : '!=' ;
LEQ : '<=' ;
GEQ : '>=' ;
LT  : '<'  ;
GT  : '>'  ;
NOT : '!'  ;
AND : '&&' ;
OR  : '||' ;

QUESTION : '?' ;
COLON    : ':' ;

TOSTR : 'toStr' ;
LEN   : 'len'   ;
NEW   : 'new'   ;

IF     : 'if'     ;
ELSE   : 'else'   ;
WHILE  : 'while'  ;
TRY    : 'try'    ;
CATCH  : 'catch'  ;
THROW  : 'throw'  ;
RETURN : 'return' ;
LAZY   : 'lazy'   ;
REF    : 'ref'    ;
VOID   : 'void'   ;
ASSIGN : '='      ;
PRINT  : 'print'  ;
NOP    : 'nop'    ;

LPAR      : '(' ;
RPAR      : ')' ;
LBRACE    : '{' ;
RBRACE    : '}' ;
LBRACK    : '[' ;
RBRACK    : ']' ;
SEMICOLON : ';' ;
COMMA     : ',' ;

BOOL : 'true' | 'false' ;
TYPE : 'int' | 'dec' | 'char' | 'bool' | 'string' ;

// I letterali numerici sono senza segno: il meno unario e' un operatore (regola 'neg'),
// cosi' '3-2' si lessicalizza come 3, -, 2 e non come 3, -2.
INT : '0' | POSDIGIT DIGIT* ;
DEC : INT '.' DIGIT+ ;

CHAR   : '\'' (~['\\\r\n] | ESC) '\'' ;
STRING : '"' STRCHR* '"' ;

fragment DIGIT    : [0-9] ;
fragment POSDIGIT : [1-9] ;
fragment STRCHR   : ~["\\\r\n] | ESC ;
fragment ESC      : '\\' [btnfr"'\\$] ;

ID : [a-zA-Z_] [a-zA-Z_0-9]* ;

LINE_COMMENT  : '//' ~[\r\n]*   -> skip ;
BLOCK_COMMENT : '/*' .*? '*/'   -> skip ;
WS            : [ \t\r\n]+      -> skip ;
