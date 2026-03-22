import Lake
open Lake DSL

package «pneuma-proofs» where
  leanOptions := #[
    ⟨`autoImplicit, false⟩
  ]

@[default_target]
lean_lib Pneuma where
  srcDir := "."

lean_lib Proofs where
  srcDir := "."
