export interface DashboardSummary {
  totalLeads: number;
  assignedLeads: number;
  activeCampaigns: number;
  callsToday: number;
  callsTodayConnected: number;
  /** 0.0 to 1.0 */
  connectionRateToday: number;
  upcomingAppointments: number;
  /** ISO date string */
  generatedAt: string;
}
