import { request } from '../../lib/request'
import type { Person } from './types'

export const peopleApi = {
    list: () => request<Person[]>('/api/people'),
}
