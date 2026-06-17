import { request } from '../../lib/request'
import type { Person } from './types'

export const listPeople = () => request<Person[]>('/api/people')
