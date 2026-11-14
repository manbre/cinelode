export function isValidFrontendUrl(value: string): boolean {
  const match =
    /^(https?):\/\/((?:[a-zA-Z0-9](?:[a-zA-Z0-9.-]*[a-zA-Z0-9])?|\[[0-9a-fA-F:]+\]))(?::([1-9][0-9]{0,4}))?\/?$/.exec(
      value,
    );

  if (!match || (match[3] && Number(match[3]) > 65535)) {
    return false;
  }

  if (match[1] === 'https') {
    return true;
  }

  const ipv4Match =
    /^(0|[1-9]\d{0,2})\.(0|[1-9]\d{0,2})\.(0|[1-9]\d{0,2})\.(0|[1-9]\d{0,2})$/.exec(
      match[2],
    );

  if (!ipv4Match) {
    return false;
  }

  const octets = ipv4Match.slice(1).map(Number);

  if (octets.some((part) => part > 255)) {
    return false;
  }

  const [a, b] = octets;

  return (
    a === 10 ||
    (a === 192 && b === 168) ||
    (a === 172 && b >= 16 && b <= 31)
  );
}
