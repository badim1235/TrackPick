# ADR-0001. Apple Preview Provider

> 상태: **Accepted**
>
> 결정일: 2026-08-30

## Context

TrackPick은 사용자가 추천된 곡을 사이트 안에서 짧게 들어볼 수 있어야 한다. YouTube embed는 광고가 재생될 수 있어 제품 요구에 맞지 않는다. 음원을 직접 다운로드해 인트로를 자르거나 재호스팅하는 방식은 저작권과 provider 정책상 채택하지 않는다.

Spotify의 preview URL은 안정적인 핵심 기능으로 의존하기 어렵고, Spotify Web Playback SDK의 전체 재생은 사용자 계정과 Premium 조건을 요구한다. Apple iTunes Search API는 Track 결과에 공식 30초 `previewUrl`을 제공한다.

## Decision

1. MVP의 첫 음악 검색·preview adapter로 Apple iTunes Search API를 사용한다.
2. 내부 provider 식별자는 `APPLE_MUSIC`으로 유지하되 외부 ID와 내부 Track PK를 분리한다.
3. Apple이 제공한 preview 원본 URL만 스트리밍하고 다운로드, 절단, 변환, 캐시 또는 재호스팅하지 않는다.
4. UI에는 `30초 미리듣기`로 표시한다.
5. preview 시작 위치는 `PROVIDER_SELECTED`로 취급하고 곡의 인트로라고 보장하지 않는다.
6. YouTube URL, video ID와 embed player를 재생 fallback으로 사용하지 않는다.
7. Apple이 요구하는 Store attribution과 외부 Track 링크를 preview 가까이에 표시한다.

## Alternatives

| 대안 | 판단 |
| --- | --- |
| YouTube IFrame Player | 광고형 재생 가능성 때문에 제외 |
| Spotify preview URL | nullable·deprecated 계약에 핵심 기능을 의존하지 않음 |
| 직접 음원 저장 후 인트로 절단 | 저작권과 provider 정책 문제로 제외 |
| MusicKit 전체 재생 | 정확한 0초 재생 후보지만 Apple Music 구독자 인증이 필요해 MVP 이후 검토 |

## Consequences

- 모든 곡에 preview가 존재한다고 보장할 수 없다.
- 30초 길이는 보장할 수 있지만 곡의 0초부터 시작한다고 보장할 수 없다.
- preview가 없는 Track도 추천, Vote와 차트 조회가 가능해야 한다.
- Apple adapter의 장애가 기존 Track 조회와 차트 기능으로 전파되지 않도록 격리해야 한다.
- provider 정책이 변경되면 adapter와 이 ADR을 재검토해야 한다.

## References

- [iTunes Search API - Understanding Search Results](https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/UnderstandingSearchResults.html)
- [iTunes Search API - Preview Content Terms](https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/index.html)
- [MusicKit](https://developer.apple.com/musickit/)
