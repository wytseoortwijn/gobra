package pkg

func example1(ghost m1 mset[int], ghost m2 mset[int]) (ghost b bool) {
  b = m1 subset m2
}

requires m1 subset m2
ensures b
func example2(ghost m1 mset[int], ghost m2 mset[int]) (ghost b bool) {
  b = true
}

requires m1 subset m2
requires m2 subset m3
ensures m1 subset m3
func example3(ghost m1 mset[int], ghost m2 mset[int], ghost m3 mset[int]) {
}

func example4(ghost m1 mset[int], ghost m2 mset[int], ghost m3 mset[int]) {
  assert m1 subset m2 intersection m3 ==> m1 subset m2 && m1 subset m3
  assert m1 subset m2 ==> m2 subset m1 ==> m1 == m2
  assert m1 subset m2 ==> m1 subset m2 union m3
  assert m1 subset m2 ==> m1 union m3 subset m2 union m3
}

ghost func example5(s mset[int], t mset[int]) {
  if (s subset t) {
  }
}

func example6() {
  assert mset[int] { 2, 2 } subset mset[int] { 1, 2, 3, 2 }
  assert mset[int] { } subset mset[int] { 2, 2 }
}

func example7(ghost m mset[int]) {
  assert mset[int] { } subset m
  assert m subset m
}