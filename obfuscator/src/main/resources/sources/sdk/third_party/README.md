# Third-party native sources

## amosnier/sha-2

- Upstream: <https://github.com/amosnier/sha-2>
- Revision: `565f65009bdd98267361b17d50cddd7c9beb3e6c`
- License: Zero-Clause BSD or Unlicense, at the recipient's option
- Local changes: `sha-256.c` is stored as `sha-256.cpp`; SPDX and provenance
  comments were added to the source and header.
- Upstream source SHA-256:
  - `sha-256.c`: `7a74437adc78576b8faff060f9573ba88a6da798914f45d263c75b149add3d27`
  - `sha-256.h`: `c2173d83813a0c29fcc3345ce489766efeadee6a52ca927ecd0f917a120df9fb`
  - `LICENSE.md`: `506de94a03e23bbc34e32a8b628b56688ac7c33672982388822816887727f1ac`

The complete upstream license text is retained in `sha-2/LICENSE.md`.

## kokke/tiny-AES-c

- Upstream: <https://github.com/kokke/tiny-AES-c>
- Revision: `23856752fbd139da0b8ca6e471a13d5bcc99a08d`
- License: Unlicense (public-domain dedication)
- Local changes: the AES-256 key schedule and block-encryption subset from
  `aes.c`/`aes.h` is stored as `aes.cpp`/`aes.h`; unused AES key sizes,
  decryption, and block modes were removed; symbols were prefixed for the SDK.
  The SDK's GCM composition is separate code in `sources/sdk/aes_gcm.cpp`.
- Original upstream SHA-256:
  - `aes.c`: `f7f78b44654efd3542b6886923dd05194286807ebcfeb078ea05d21fe901ee9e`
  - `aes.h`: `9f74a4de3bd11621ff6e8fb9b352b060f699e6c1c67f81a22d104aacad61a23b`
  - `unlicense.txt`: `7e12e5df4bae12cb21581ba157ced20e1986a0508dd10d0e8a4ab9a4cf94e85c`

The complete license text is retained in `tiny-aes-c/UNLICENSE.txt`.
