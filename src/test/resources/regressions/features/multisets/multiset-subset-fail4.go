package pkg

func foo(ghost m1 mset[int], ghost m2 mset[int], ghost m3 mset[int]) {
  //:: ExpectedOutput(assert_error:assertion_error)
  assert m1 subset m3 ==> m2 subset m3 ==> m1 subset m2
}
