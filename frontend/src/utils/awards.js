export function getAwardWins(content) {
  const awards = [
    { type: 'oscar', count: content.oscarWins },
    { type: 'emmy', count: content.primetimeEmmyWins },
  ]

  return awards.filter(({ count }) => Number.isSafeInteger(count) && count > 0)
}
