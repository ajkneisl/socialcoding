import { useQuery } from '@tanstack/react-query'
import { listPeople } from './api'

export const peopleKeys = {
    all: ['people'] as const,
}

export function usePeople() {
    return useQuery({
        queryKey: peopleKeys.all,
        queryFn: listPeople,
    })
}
