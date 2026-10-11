export function startOfWeek(date: Date): Date {
  const day = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const weekday = day.getDay();
  const offset = weekday === 0 ? -6 : 1 - weekday;
  day.setDate(day.getDate() + offset);
  return day;
}

export function addDays(date: Date, days: number): Date {
  const next = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  next.setDate(next.getDate() + days);
  return next;
}

export function isoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

export function parseIsoDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day);
}

export function weekDays(monday: Date): Date[] {
  return Array.from({ length: 7 }, (_, index) => addDays(monday, index));
}

const WEEKDAYS = ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag'];
const MONTHS = [
  'Januar',
  'Februar',
  'März',
  'April',
  'Mai',
  'Juni',
  'Juli',
  'August',
  'September',
  'Oktober',
  'November',
  'Dezember',
];

export function dayLabel(date: Date): string {
  return `${WEEKDAYS[date.getDay()]}, ${date.getDate()}. ${MONTHS[date.getMonth()]}`;
}

export function weekLabel(monday: Date): string {
  const sunday = addDays(monday, 6);
  if (monday.getMonth() === sunday.getMonth() && monday.getFullYear() === sunday.getFullYear()) {
    return `${monday.getDate()}.–${sunday.getDate()}. ${MONTHS[sunday.getMonth()]} ${sunday.getFullYear()}`;
  }
  const startYear = monday.getFullYear() === sunday.getFullYear() ? '' : ` ${monday.getFullYear()}`;
  return `${monday.getDate()}. ${MONTHS[monday.getMonth()]}${startYear} – ${sunday.getDate()}. ${MONTHS[sunday.getMonth()]} ${sunday.getFullYear()}`;
}

export function todayIso(): string {
  return isoDate(new Date());
}

export function shiftIsoDate(value: string, days: number): string {
  return isoDate(addDays(parseIsoDate(value), days));
}
