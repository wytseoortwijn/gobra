package main

//:: ExpectedOutput(parser_error)
import "a"
//:: ExpectedOutput(parser_error)
import ("b")
//:: ExpectedOutput(parser_error)
import ("c");
import (
    //:: ExpectedOutput(parser_error)
    "d"
    e "e")
import (
    f "f"
    //:: ExpectedOutput(parser_error)
    "g"
)
