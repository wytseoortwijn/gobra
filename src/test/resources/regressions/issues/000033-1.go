package main

func foo() {
  r := Rectangle{Width: 2, Height: 5}
  assert r.Area() == 10
}

type Rectangle struct {
    Width, Height int
}

ensures res == r.Width * r.Height
func (r Rectangle) Area() (res int) {
    return r.Width * r.Height
}

pure func (r Rectangle) Area2() (res int) {
    return r.Width * r.Height
}