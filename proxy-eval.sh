#!/bin/bash

if [[ "$1" == "" ]]
then
    echo You must give the benchmark to evaluate.
    exit
else
    BENCH=$1
fi

DB1=out/context-insensitive/${BENCH}/database
DB2=out/context-insensitive/${BENCH}-reflection-dynamic-proxies/database

echo Evaluation of dynamic proxy analysis, BENCH=${BENCH}


echo '== Are newProxyInstance() calls reachable? =='
echo 'Using the context-insensitive/no-proxies case:'
bloxbatch -db ${DB1} -query '_(?m) <-
Reachable(?m),
Instruction:Method[?invo] = ?m,
StaticMethodInvocation:SimpleName[?invo] = "newProxyInstance"
.'

# echo '== are getProxyClass() calls reachable? =='
# echo 'Using the context-insensitive/no-proxies case:'
# bloxbatch -db ${DB1} -query '_(?m) <-
# Reachable(?m),
# Instruction:Method[?invo] = ?m,
# StaticMethodInvocation:SimpleName[?invo] = "getProxyClass"
# .'

echo '== Are invocation handler methods made reachable? =='

echo "Without proxy support (${DB1}):"
bloxbatch -db ${DB1} -query '_(?m) <-
Type:Id(?invocationHandlerType:"java.lang.reflect.InvocationHandler"),
SubtypeOf(?customInvocationHandlerType, ?invocationHandlerType),
Method:DeclaringType[?m] = ?customInvocationHandlerType,
Reachable(?m)
.'

echo "With proxy support (${DB2}):"
bloxbatch -db ${DB2} -query '_(?m) <-
Type:Id(?invocationHandlerType:"java.lang.reflect.InvocationHandler"),
SubtypeOf(?customInvocationHandlerType, ?invocationHandlerType),
Method:DeclaringType[?m] = ?customInvocationHandlerType,
Reachable(?m)
.'

echo '== Proxy relations =='
echo 'Without proxy support:'
bloxbatch -db ${DB1} -popCount | grep Proxy
echo 'With proxy support:'
bloxbatch -db ${DB2} -popCount | grep Proxy
