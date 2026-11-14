import { getAwardWins } from '../utils/awards'

function AwardIcon({ type }) {
  return (
    <svg
      aria-hidden="true"
      className="h-[1.25em] w-[1.25em] shrink-0"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {type === 'oscar' ? (
        <>
          <circle cx="12" cy="3.5" r="2" />
          <path d="M8.5 9.5 10 7h4l1.5 2.5L14 12l-.5 6h-3L10 12 8.5 9.5Z" />
          <path d="m8.5 9.5 3.5 2 3.5-2M9 20h6M8 22h8" />
        </>
      ) : (
        <>
          <circle cx="12" cy="4.5" r="2.5" />
          <path d="M10 7.5 8.5 11 5 8.5 2.5 4l1 6 4.5 4 2.5 1.5L11 19M14 7.5l1.5 3.5L19 8.5 21.5 4l-1 6-4.5 4-2.5 1.5L13 19M9 21h6M8 23h8" />
        </>
      )}
    </svg>
  )
}

export default function AwardWins({ content }) {
  return getAwardWins(content).map(({ type, count }) => {
    const award = type === 'oscar' ? 'Oscar' : 'Primetime Emmy'
    const label = `${count} ${award} ${count === 1 ? 'win' : 'wins'}`
    return (
      <span key={type} aria-label={label} title={label} className="inline-flex items-center gap-1 whitespace-nowrap">
        <AwardIcon type={type} />
        <span aria-hidden="true">{count}</span>
      </span>
    )
  })
}
