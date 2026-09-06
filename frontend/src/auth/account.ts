import { useQuery } from '@tanstack/react-query'
import { fetchAccount } from '../api/client'

export const accountQueryKey = ['account'] as const

type AccountQueryOptions = {
  fresh?: boolean
}

export function useAccount({ fresh = false }: AccountQueryOptions = {}) {
  return useQuery({
    queryKey: accountQueryKey,
    queryFn: fetchAccount,
    retry: false,
    staleTime: fresh ? 0 : 30_000,
    refetchOnMount: fresh ? 'always' : true,
  })
}
